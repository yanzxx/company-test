package vip.xiaonuo.biz.modular.emergency.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vip.xiaonuo.auth.core.pojo.SaBaseLoginUser;
import vip.xiaonuo.auth.core.util.StpLoginUserUtil;
import vip.xiaonuo.biz.modular.emergency.param.EmergencyLogQueryParam;
import vip.xiaonuo.biz.modular.emergency.param.EmergencyPoiQueryParam;
import vip.xiaonuo.biz.modular.emergency.param.EmergencyRadiusUpdateParam;
import vip.xiaonuo.biz.modular.emergency.result.EmergencyDrillLogResult;
import vip.xiaonuo.biz.modular.emergency.result.EmergencyLeakPointResult;
import vip.xiaonuo.biz.modular.emergency.result.EmergencyPoiQueryResult;
import vip.xiaonuo.biz.modular.emergency.result.EmergencyPoiResult;
import vip.xiaonuo.biz.modular.emergency.result.EmergencyScenarioStateResult;
import vip.xiaonuo.biz.modular.emergency.result.EmergencySignalResult;
import vip.xiaonuo.biz.modular.emergency.service.EmergencyDrillService;
import vip.xiaonuo.common.exception.CommonException;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 应急演练大屏Service实现类
 *
 * @author Codex
 * @date 2026/3/19
 */
@Slf4j
@Service
public class EmergencyDrillServiceImpl implements EmergencyDrillService {

    private static final double EARTH_RADIUS_METERS = 6371000D;
    private static final double DEFAULT_CENTER_LNG = 113.947321D;
    private static final double DEFAULT_CENTER_LAT = 22.543211D;
    private static final double DEFAULT_RADIUS_METERS = 1500D;
    private static final int MOCK_POI_TOTAL = 100;
    private static final int DEFAULT_LOG_LIMIT = 50;
    private static final int MAX_LOG_LIMIT = 200;
    private static final int MAX_LOG_SIZE = 500;
    private static final int MAX_LEAK_POINT_SIZE = 20;
    private static final long DISASTER_SIGNAL_INTERVAL_SECONDS = 10L;
    private static final String SYSTEM_OPERATOR_ID = "SYSTEM";
    private static final String SYSTEM_OPERATOR_NAME = "系统模拟器";
    private static final String LOG_FILE_PATH = "app-log/emergency-drill-log.json";

    private final List<EmergencyPoiResult> mockPoiCatalog = new CopyOnWriteArrayList<>();
    private final List<SseEmitter> emitterList = new CopyOnWriteArrayList<>();
    private final Deque<EmergencyLeakPointResult> leakPointDeque = new ConcurrentLinkedDeque<>();
    private final List<EmergencyDrillLogResult> drillLogList = new ArrayList<>();
    private final AtomicLong signalCounter = new AtomicLong();
    private final Object stateLock = new Object();
    private final Object logLock = new Object();

    private final ScenarioState scenarioState = new ScenarioState();

    private volatile EmergencySignalResult latestSignal;
    private ScheduledExecutorService scheduledExecutorService;

    @PostConstruct
    public void init() {
        if (mockPoiCatalog.isEmpty()) {
            mockPoiCatalog.addAll(buildMockPoiCatalog());
        }
        scenarioState.setCenterLng(DEFAULT_CENTER_LNG);
        scenarioState.setCenterLat(DEFAULT_CENTER_LAT);
        scenarioState.setRadiusMeters(DEFAULT_RADIUS_METERS);
        loadPersistedLogs();
        appendLog("SYSTEM_INIT", "初始化应急演练场景",
                StrUtil.format("已载入{}条模拟POI，并启动{}秒一次的灾情推送。", mockPoiCatalog.size(),
                        DISASTER_SIGNAL_INTERVAL_SECONDS), SYSTEM_OPERATOR_ID, SYSTEM_OPERATOR_NAME);
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "emergency-drill-simulator");
            thread.setDaemon(true);
            return thread;
        });
        scheduledExecutorService.scheduleAtFixedRate(this::safeGenerateSignal, DISASTER_SIGNAL_INTERVAL_SECONDS,
                DISASTER_SIGNAL_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void destroy() {
        if (ObjectUtil.isNotEmpty(scheduledExecutorService)) {
            scheduledExecutorService.shutdownNow();
        }
        emitterList.forEach(SseEmitter::complete);
        emitterList.clear();
    }

    @Override
    public List<EmergencyPoiResult> mockPoiList() {
        return mockPoiCatalog.stream().map(this::copyPoi).collect(Collectors.toList());
    }

    @Override
    public EmergencyPoiQueryResult queryInRange(EmergencyPoiQueryParam emergencyPoiQueryParam) {
        List<EmergencyPoiResult> matchedPoiList = filterPoiInRange(emergencyPoiQueryParam.getCenterLng(),
                emergencyPoiQueryParam.getCenterLat(), emergencyPoiQueryParam.getRadiusMeters());
        appendCurrentUserLog("POI_QUERY", "查询受灾范围内POI",
                StrUtil.format("中心点({}, {}) 半径{}米，共命中{}个POI。",
                        round(emergencyPoiQueryParam.getCenterLng(), 6),
                        round(emergencyPoiQueryParam.getCenterLat(), 6),
                        round(emergencyPoiQueryParam.getRadiusMeters(), 2),
                        matchedPoiList.size()));
        EmergencyPoiQueryResult emergencyPoiQueryResult = new EmergencyPoiQueryResult();
        emergencyPoiQueryResult.setCenterLng(round(emergencyPoiQueryParam.getCenterLng(), 6));
        emergencyPoiQueryResult.setCenterLat(round(emergencyPoiQueryParam.getCenterLat(), 6));
        emergencyPoiQueryResult.setRadiusMeters(round(emergencyPoiQueryParam.getRadiusMeters(), 2));
        emergencyPoiQueryResult.setTotalPoiCount(mockPoiCatalog.size());
        emergencyPoiQueryResult.setMatchedCount(matchedPoiList.size());
        emergencyPoiQueryResult.setPoiList(matchedPoiList);
        return emergencyPoiQueryResult;
    }

    @Override
    public EmergencyScenarioStateResult disasterState() {
        return buildScenarioState();
    }

    @Override
    public EmergencyScenarioStateResult updateRadius(EmergencyRadiusUpdateParam emergencyRadiusUpdateParam) {
        boolean hasCenterLng = ObjectUtil.isNotNull(emergencyRadiusUpdateParam.getCenterLng());
        boolean hasCenterLat = ObjectUtil.isNotNull(emergencyRadiusUpdateParam.getCenterLat());
        if (hasCenterLng != hasCenterLat) {
            throw new CommonException("中心点经纬度需要同时传入");
        }
        double beforeRadius;
        double afterRadius;
        double centerLng;
        double centerLat;
        synchronized (stateLock) {
            beforeRadius = scenarioState.getRadiusMeters();
            scenarioState.setRadiusMeters(emergencyRadiusUpdateParam.getRadiusMeters());
            if (hasCenterLng) {
                scenarioState.setCenterLng(emergencyRadiusUpdateParam.getCenterLng());
                scenarioState.setCenterLat(emergencyRadiusUpdateParam.getCenterLat());
            }
            afterRadius = scenarioState.getRadiusMeters();
            centerLng = scenarioState.getCenterLng();
            centerLat = scenarioState.getCenterLat();
        }
        String detail = StrUtil.format("半径更新为{}米{}{}",
                        round(emergencyRadiusUpdateParam.getRadiusMeters(), 2),
                        hasCenterLng ? "，中心点调整为(" + round(emergencyRadiusUpdateParam.getCenterLng(), 6) + ", "
                                + round(emergencyRadiusUpdateParam.getCenterLat(), 6) + ")" : "",
                        StrUtil.isBlank(emergencyRadiusUpdateParam.getRemark()) ? ""
                                : "，备注：" + emergencyRadiusUpdateParam.getRemark());
        appendCurrentUserLog("RADIUS_UPDATE", "手动更新受灾区域参数", detail);
        EmergencySignalResult manualUpdateSignal = new EmergencySignalResult();
        manualUpdateSignal.setSignalId(IdUtil.fastSimpleUUID());
        manualUpdateSignal.setSignalNo(signalCounter.incrementAndGet());
        manualUpdateSignal.setSignalType("MANUAL_RADIUS_UPDATE");
        manualUpdateSignal.setSignalName("人工调整受灾范围");
        manualUpdateSignal.setMessage(detail);
        manualUpdateSignal.setSignalTime(new Date());
        manualUpdateSignal.setCenterLng(round(centerLng, 6));
        manualUpdateSignal.setCenterLat(round(centerLat, 6));
        manualUpdateSignal.setRadiusMeters(round(afterRadius, 2));
        manualUpdateSignal.setRadiusDeltaMeters(round(afterRadius - beforeRadius, 2));
        manualUpdateSignal.setAffectedPoiCount(filterPoiInRange(centerLng, centerLat, afterRadius).size());
        latestSignal = manualUpdateSignal;
        broadcastSignal(manualUpdateSignal);
        return buildScenarioState();
    }

    @Override
    public SseEmitter disasterStream() {
        SseEmitter sseEmitter = new SseEmitter(0L);
        emitterList.add(sseEmitter);
        sseEmitter.onCompletion(() -> emitterList.remove(sseEmitter));
        sseEmitter.onTimeout(() -> emitterList.remove(sseEmitter));
        sseEmitter.onError(throwable -> emitterList.remove(sseEmitter));
        try {
            sseEmitter.send(SseEmitter.event().name("snapshot").data(buildScenarioState()));
        } catch (Exception e) {
            emitterList.remove(sseEmitter);
            sseEmitter.completeWithError(e);
        }
        return sseEmitter;
    }

    @Override
    public List<EmergencyDrillLogResult> logList(EmergencyLogQueryParam emergencyLogQueryParam) {
        int limit = ObjectUtil.isEmpty(emergencyLogQueryParam) || ObjectUtil.isEmpty(emergencyLogQueryParam.getLimit())
                ? DEFAULT_LOG_LIMIT : Math.min(emergencyLogQueryParam.getLimit(), MAX_LOG_LIMIT);
        synchronized (logLock) {
            List<EmergencyDrillLogResult> resultList = new ArrayList<>(drillLogList);
            Collections.reverse(resultList);
            if (resultList.size() > limit) {
                return new ArrayList<>(resultList.subList(0, limit));
            }
            return resultList;
        }
    }

    private void safeGenerateSignal() {
        try {
            EmergencySignalResult emergencySignalResult = generateSignal();
            latestSignal = emergencySignalResult;
            broadcastSignal(emergencySignalResult);
            appendLog("SYSTEM_SIGNAL", emergencySignalResult.getSignalName(), emergencySignalResult.getMessage(),
                    SYSTEM_OPERATOR_ID, SYSTEM_OPERATOR_NAME);
        } catch (Exception e) {
            log.error("generate disaster signal error", e);
        }
    }

    private EmergencySignalResult generateSignal() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean generateLeakPoint = random.nextBoolean();
        EmergencySignalResult emergencySignalResult = new EmergencySignalResult();
        emergencySignalResult.setSignalId(IdUtil.fastSimpleUUID());
        emergencySignalResult.setSignalNo(signalCounter.incrementAndGet());
        emergencySignalResult.setSignalTime(new Date());

        double centerLng;
        double centerLat;
        double radiusMeters;
        synchronized (stateLock) {
            centerLng = scenarioState.getCenterLng();
            centerLat = scenarioState.getCenterLat();
            radiusMeters = scenarioState.getRadiusMeters();
            if (generateLeakPoint) {
                EmergencyLeakPointResult emergencyLeakPointResult = createLeakPoint(centerLng, centerLat, radiusMeters,
                        emergencySignalResult.getSignalNo());
                leakPointDeque.addFirst(emergencyLeakPointResult);
                while (leakPointDeque.size() > MAX_LEAK_POINT_SIZE) {
                    leakPointDeque.pollLast();
                }
                emergencySignalResult.setSignalType("NEW_LEAK_POINT");
                emergencySignalResult.setSignalName("新的漏水点");
                emergencySignalResult.setRadiusDeltaMeters(0D);
                emergencySignalResult.setLeakPoint(copyLeakPoint(emergencyLeakPointResult));
                emergencySignalResult.setMessage(StrUtil.format("发现{}，位置({}, {})，请就近派发巡检队核实。",
                        emergencyLeakPointResult.getName(), emergencyLeakPointResult.getLng(),
                        emergencyLeakPointResult.getLat()));
            } else {
                double radiusDelta = random.nextDouble(80D, 260D);
                radiusMeters = radiusMeters + radiusDelta;
                scenarioState.setRadiusMeters(radiusMeters);
                emergencySignalResult.setSignalType("RISK_AGGRAVATED");
                emergencySignalResult.setSignalName("险情加重");
                emergencySignalResult.setRadiusDeltaMeters(round(radiusDelta, 2));
                emergencySignalResult.setMessage(StrUtil.format("受灾范围扩大{}米，当前影响半径升至{}米。",
                        round(radiusDelta, 2), round(radiusMeters, 2)));
            }
        }
        emergencySignalResult.setCenterLng(round(centerLng, 6));
        emergencySignalResult.setCenterLat(round(centerLat, 6));
        emergencySignalResult.setRadiusMeters(round(radiusMeters, 2));
        emergencySignalResult.setAffectedPoiCount(filterPoiInRange(centerLng, centerLat, radiusMeters).size());
        return emergencySignalResult;
    }

    private void broadcastSignal(EmergencySignalResult emergencySignalResult) {
        for (SseEmitter sseEmitter : emitterList) {
            try {
                sseEmitter.send(SseEmitter.event()
                        .id(emergencySignalResult.getSignalId())
                        .name("signal")
                        .data(emergencySignalResult));
            } catch (Exception e) {
                emitterList.remove(sseEmitter);
                sseEmitter.completeWithError(e);
            }
        }
    }

    private EmergencyScenarioStateResult buildScenarioState() {
        double centerLng;
        double centerLat;
        double radiusMeters;
        synchronized (stateLock) {
            centerLng = scenarioState.getCenterLng();
            centerLat = scenarioState.getCenterLat();
            radiusMeters = scenarioState.getRadiusMeters();
        }
        EmergencyScenarioStateResult emergencyScenarioStateResult = new EmergencyScenarioStateResult();
        emergencyScenarioStateResult.setCenterLng(round(centerLng, 6));
        emergencyScenarioStateResult.setCenterLat(round(centerLat, 6));
        emergencyScenarioStateResult.setRadiusMeters(round(radiusMeters, 2));
        emergencyScenarioStateResult.setTotalPoiCount(mockPoiCatalog.size());
        emergencyScenarioStateResult.setPoiCountInRange(filterPoiInRange(centerLng, centerLat, radiusMeters).size());
        emergencyScenarioStateResult.setSignalCounter(signalCounter.get());
        emergencyScenarioStateResult.setLatestSignal(copySignal(latestSignal));
        emergencyScenarioStateResult.setLeakPointList(leakPointDeque.stream().map(this::copyLeakPoint).collect(Collectors.toList()));
        return emergencyScenarioStateResult;
    }

    private List<EmergencyPoiResult> filterPoiInRange(double centerLng, double centerLat, double radiusMeters) {
        return mockPoiCatalog.stream().map(this::copyPoi).peek(emergencyPoiResult -> emergencyPoiResult
                        .setDistanceMeters(round(calculateDistanceMeters(centerLng, centerLat, emergencyPoiResult.getLng(),
                                emergencyPoiResult.getLat()), 2)))
                .filter(emergencyPoiResult -> emergencyPoiResult.getDistanceMeters() <= round(radiusMeters, 2))
                .sorted((left, right) -> left.getDistanceMeters().compareTo(right.getDistanceMeters()))
                .collect(Collectors.toList());
    }

    private List<EmergencyPoiResult> buildMockPoiCatalog() {
        String[] districtArray = {"滨江", "南山", "福田", "罗湖", "盐田", "龙岗", "宝安", "龙华", "光明", "坪山"};
        String[] facilityArray = {"中心医院", "实验学校", "应急避险点", "变电站", "消防站", "物资仓库", "社区服务站", "公交枢纽", "排涝泵站", "指挥前置点"};
        String[] typeArray = {"医院", "学校", "避险点", "电力设施", "消防设施", "物资仓储", "社区服务", "交通枢纽", "排涝设施", "应急指挥"};
        List<EmergencyPoiResult> resultList = new ArrayList<>(MOCK_POI_TOTAL);
        for (int i = 0; i < districtArray.length; i++) {
            int districtCol = i % 5;
            int districtRow = i / 5;
            double districtEastMeters = (districtCol - 2D) * 1650D + (i % 2 == 0 ? 180D : -180D);
            double districtNorthMeters = (districtRow - 0.5D) * 1850D + ((i % 3) - 1) * 120D;
            for (int j = 0; j < facilityArray.length; j++) {
                int facilityCol = j % 5;
                int facilityRow = j / 5;
                double facilityEastMeters = (facilityCol - 2D) * 260D + (facilityRow == 0 ? -80D : 80D);
                double facilityNorthMeters = (facilityRow - 0.5D) * 480D + (facilityCol % 2 == 0 ? 60D : -60D);
                double eastMeters = districtEastMeters + facilityEastMeters;
                double northMeters = districtNorthMeters + facilityNorthMeters;
                EmergencyPoiResult emergencyPoiResult = new EmergencyPoiResult();
                emergencyPoiResult.setId(String.format("poi-%03d", i * facilityArray.length + j + 1));
                emergencyPoiResult.setName(districtArray[i] + facilityArray[j]);
                emergencyPoiResult.setType(typeArray[j]);
                emergencyPoiResult.setLng(round(offsetLng(DEFAULT_CENTER_LNG, DEFAULT_CENTER_LAT, eastMeters), 6));
                emergencyPoiResult.setLat(round(offsetLat(DEFAULT_CENTER_LAT, northMeters), 6));
                resultList.add(emergencyPoiResult);
            }
        }
        return resultList;
    }

    private EmergencyLeakPointResult createLeakPoint(double centerLng, double centerLat, double radiusMeters, Long signalNo) {
        double angle = ThreadLocalRandom.current().nextDouble(0D, Math.PI * 2D);
        double distance = radiusMeters * ThreadLocalRandom.current().nextDouble(0.35D, 1.05D);
        double eastMeters = Math.cos(angle) * distance;
        double northMeters = Math.sin(angle) * distance;
        EmergencyLeakPointResult emergencyLeakPointResult = new EmergencyLeakPointResult();
        emergencyLeakPointResult.setId(IdUtil.fastSimpleUUID());
        emergencyLeakPointResult.setName(String.format("漏水点-%03d", signalNo));
        emergencyLeakPointResult.setLng(round(offsetLng(centerLng, centerLat, eastMeters), 6));
        emergencyLeakPointResult.setLat(round(offsetLat(centerLat, northMeters), 6));
        emergencyLeakPointResult.setDetectedTime(new Date());
        return emergencyLeakPointResult;
    }

    private double calculateDistanceMeters(double centerLng, double centerLat, double targetLng, double targetLat) {
        double latDistance = Math.toRadians(targetLat - centerLat);
        double lngDistance = Math.toRadians(targetLng - centerLng);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(centerLat)) * Math.cos(Math.toRadians(targetLat))
                * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    private double offsetLng(double centerLng, double centerLat, double eastMeters) {
        return centerLng + eastMeters / (111320D * Math.cos(Math.toRadians(centerLat)));
    }

    private double offsetLat(double centerLat, double northMeters) {
        return centerLat + northMeters / 110540D;
    }

    private double round(double value, int scale) {
        return NumberUtil.round(value, scale).doubleValue();
    }

    private EmergencyPoiResult copyPoi(EmergencyPoiResult emergencyPoiResult) {
        if (ObjectUtil.isEmpty(emergencyPoiResult)) {
            return null;
        }
        EmergencyPoiResult result = new EmergencyPoiResult();
        result.setId(emergencyPoiResult.getId());
        result.setName(emergencyPoiResult.getName());
        result.setType(emergencyPoiResult.getType());
        result.setLng(emergencyPoiResult.getLng());
        result.setLat(emergencyPoiResult.getLat());
        result.setDistanceMeters(emergencyPoiResult.getDistanceMeters());
        return result;
    }

    private EmergencyLeakPointResult copyLeakPoint(EmergencyLeakPointResult emergencyLeakPointResult) {
        if (ObjectUtil.isEmpty(emergencyLeakPointResult)) {
            return null;
        }
        EmergencyLeakPointResult result = new EmergencyLeakPointResult();
        result.setId(emergencyLeakPointResult.getId());
        result.setName(emergencyLeakPointResult.getName());
        result.setLng(emergencyLeakPointResult.getLng());
        result.setLat(emergencyLeakPointResult.getLat());
        result.setDetectedTime(emergencyLeakPointResult.getDetectedTime());
        return result;
    }

    private EmergencySignalResult copySignal(EmergencySignalResult emergencySignalResult) {
        if (ObjectUtil.isEmpty(emergencySignalResult)) {
            return null;
        }
        EmergencySignalResult result = new EmergencySignalResult();
        result.setSignalId(emergencySignalResult.getSignalId());
        result.setSignalNo(emergencySignalResult.getSignalNo());
        result.setSignalType(emergencySignalResult.getSignalType());
        result.setSignalName(emergencySignalResult.getSignalName());
        result.setMessage(emergencySignalResult.getMessage());
        result.setSignalTime(emergencySignalResult.getSignalTime());
        result.setCenterLng(emergencySignalResult.getCenterLng());
        result.setCenterLat(emergencySignalResult.getCenterLat());
        result.setRadiusMeters(emergencySignalResult.getRadiusMeters());
        result.setRadiusDeltaMeters(emergencySignalResult.getRadiusDeltaMeters());
        result.setAffectedPoiCount(emergencySignalResult.getAffectedPoiCount());
        result.setLeakPoint(copyLeakPoint(emergencySignalResult.getLeakPoint()));
        return result;
    }

    private void appendCurrentUserLog(String operationType, String operationName, String detail) {
        SaBaseLoginUser loginUser = getCurrentLoginUser();
        appendLog(operationType, operationName, detail,
                ObjectUtil.isNotEmpty(loginUser) ? loginUser.getId() : "UNKNOWN",
                ObjectUtil.isNotEmpty(loginUser) ? loginUser.getName() : "匿名用户");
    }

    private SaBaseLoginUser getCurrentLoginUser() {
        try {
            return StpLoginUserUtil.getLoginUser();
        } catch (Exception e) {
            return null;
        }
    }

    private void appendLog(String operationType, String operationName, String detail, String operatorId, String operatorName) {
        synchronized (logLock) {
            EmergencyDrillLogResult emergencyDrillLogResult = new EmergencyDrillLogResult();
            emergencyDrillLogResult.setId(IdUtil.fastSimpleUUID());
            emergencyDrillLogResult.setOperationType(operationType);
            emergencyDrillLogResult.setOperationName(operationName);
            emergencyDrillLogResult.setOperatorId(operatorId);
            emergencyDrillLogResult.setOperatorName(operatorName);
            emergencyDrillLogResult.setDetail(detail);
            emergencyDrillLogResult.setCreateTime(new Date());
            drillLogList.add(emergencyDrillLogResult);
            while (drillLogList.size() > MAX_LOG_SIZE) {
                drillLogList.remove(0);
            }
            persistLogs();
        }
    }

    private void loadPersistedLogs() {
        File logFile = FileUtil.file(System.getProperty("user.dir"), LOG_FILE_PATH);
        if (!logFile.exists()) {
            return;
        }
        String logJson = FileUtil.readUtf8String(logFile);
        if (StrUtil.isBlank(logJson)) {
            return;
        }
        synchronized (logLock) {
            drillLogList.clear();
            drillLogList.addAll(JSONUtil.toList(logJson, EmergencyDrillLogResult.class));
        }
    }

    private void persistLogs() {
        File logFile = FileUtil.file(System.getProperty("user.dir"), LOG_FILE_PATH);
        FileUtil.mkParentDirs(logFile);
        FileUtil.writeUtf8String(JSONUtil.toJsonPrettyStr(drillLogList), logFile);
    }

    /**
     * 演练场景状态
     */
    private static class ScenarioState {

        private double centerLng;

        private double centerLat;

        private double radiusMeters;

        public double getCenterLng() {
            return centerLng;
        }

        public void setCenterLng(double centerLng) {
            this.centerLng = centerLng;
        }

        public double getCenterLat() {
            return centerLat;
        }

        public void setCenterLat(double centerLat) {
            this.centerLat = centerLat;
        }

        public double getRadiusMeters() {
            return radiusMeters;
        }

        public void setRadiusMeters(double radiusMeters) {
            this.radiusMeters = radiusMeters;
        }
    }
}
