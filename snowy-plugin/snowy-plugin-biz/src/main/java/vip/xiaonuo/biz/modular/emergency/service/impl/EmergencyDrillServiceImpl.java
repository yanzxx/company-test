package vip.xiaonuo.biz.modular.emergency.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vip.xiaonuo.biz.modular.emergency.entity.EmergencyFacility;
import vip.xiaonuo.biz.modular.emergency.mapper.EmergencyFacilityMapper;
import vip.xiaonuo.auth.core.pojo.SaBaseLoginUser;
import vip.xiaonuo.auth.core.util.StpLoginUserUtil;
import vip.xiaonuo.biz.modular.emergency.param.EmergencyLogQueryParam;
import vip.xiaonuo.biz.modular.emergency.param.EmergencyLogRecordParam;
import vip.xiaonuo.biz.modular.emergency.param.EmergencyPoiQueryParam;
import vip.xiaonuo.biz.modular.emergency.param.EmergencyRadiusUpdateParam;
import vip.xiaonuo.biz.modular.emergency.result.EmergencyAiSuggestionResult;
import vip.xiaonuo.biz.modular.emergency.result.EmergencyDrillLogResult;
import vip.xiaonuo.biz.modular.emergency.result.EmergencyFacilityStatResult;
import vip.xiaonuo.biz.modular.emergency.result.EmergencyLeakPointResult;
import vip.xiaonuo.biz.modular.emergency.result.EmergencyPoiQueryResult;
import vip.xiaonuo.biz.modular.emergency.result.EmergencyPoiResult;
import vip.xiaonuo.biz.modular.emergency.result.EmergencyRescueRouteResult;
import vip.xiaonuo.biz.modular.emergency.result.EmergencyRoutePointResult;
import vip.xiaonuo.biz.modular.emergency.result.EmergencyScenarioStateResult;
import vip.xiaonuo.biz.modular.emergency.result.EmergencySignalResult;
import vip.xiaonuo.biz.modular.emergency.service.EmergencyDrillService;
import vip.xiaonuo.common.exception.CommonException;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private static final int DEFAULT_LOG_LIMIT = 50;
    private static final int MAX_LOG_LIMIT = 200;
    private static final int MAX_LOG_SIZE = 500;
    private static final int MAX_LEAK_POINT_SIZE = 20;
    private static final int INITIAL_LEAK_POINT_SIZE = 2;
    private static final double LEAK_POINT_ANCHOR_RADIUS_RATIO = 0.88D;
    private static final double LEAK_POINT_INWARD_OFFSET_BASE_METERS = 8D;
    private static final double LEAK_POINT_INWARD_OFFSET_SPAN_METERS = 8D;
    private static final double LEAK_POINT_LATERAL_OFFSET_BASE_METERS = 3D;
    private static final double LEAK_POINT_LATERAL_OFFSET_SPAN_METERS = 5D;
    private static final long DISASTER_SIGNAL_INTERVAL_SECONDS = 10L;
    private static final String SYSTEM_OPERATOR_ID = "SYSTEM";
    private static final String SYSTEM_OPERATOR_NAME = "系统模拟器";
    private static final String LOG_FILE_PATH = "app-log/emergency-drill-log.json";
    private static final String FACILITY_DATA_MISSING_MESSAGE = "应急设施表未初始化，请执行数据库更新脚本后重试";

    @Resource
    private EmergencyFacilityMapper emergencyFacilityMapper;

    private final List<EmergencyPoiResult> facilityCatalog = new CopyOnWriteArrayList<>();
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
        reloadFacilityCatalog(false);
        scenarioState.setCenterLng(DEFAULT_CENTER_LNG);
        scenarioState.setCenterLat(DEFAULT_CENTER_LAT);
        scenarioState.setRadiusMeters(DEFAULT_RADIUS_METERS);
        initializeLeakPoints();
        loadPersistedLogs();
        appendLog("SYSTEM_INIT", "初始化应急演练场景",
                StrUtil.format("已载入{}条数据库设施点位，预置{}个漏水点，并启动{}秒一次的灾情推送。", facilityCatalog.size(),
                        leakPointDeque.size(), DISASTER_SIGNAL_INTERVAL_SECONDS), SYSTEM_OPERATOR_ID, SYSTEM_OPERATOR_NAME);
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
        reloadFacilityCatalog(true);
        return facilityCatalog.stream().map(this::copyPoi).collect(Collectors.toList());
    }

    @Override
    public EmergencyPoiQueryResult queryInRange(EmergencyPoiQueryParam emergencyPoiQueryParam) {
        reloadFacilityCatalog(true);
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
        emergencyPoiQueryResult.setTotalPoiCount(facilityCatalog.size());
        emergencyPoiQueryResult.setMatchedCount(matchedPoiList.size());
        emergencyPoiQueryResult.setPoiList(matchedPoiList);
        return emergencyPoiQueryResult;
    }

    @Override
    public EmergencyScenarioStateResult disasterState() {
        reloadFacilityCatalog(true);
        return buildScenarioState();
    }

    @Override
    public EmergencyAiSuggestionResult aiSuggestion() {
        reloadFacilityCatalog(true);
        return buildAiSuggestion(buildScenarioState());
    }

    @Override
    public EmergencyScenarioStateResult updateRadius(EmergencyRadiusUpdateParam emergencyRadiusUpdateParam) {
        reloadFacilityCatalog(true);
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
        List<EmergencyPoiResult> affectedPoiList = filterPoiInRange(centerLng, centerLat, afterRadius);
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
        manualUpdateSignal.setAffectedPoiCount(affectedPoiList.size());
        manualUpdateSignal.setAffectedPoiList(affectedPoiList);
        latestSignal = manualUpdateSignal;
        broadcastSignal(manualUpdateSignal);
        return buildScenarioState();
    }

    @Override
    public SseEmitter disasterStream() {
        reloadFacilityCatalog(true);
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

    @Override
    public void recordLog(EmergencyLogRecordParam emergencyLogRecordParam) {
        String detail = StrUtil.blankToDefault(StrUtil.trim(emergencyLogRecordParam.getDetail()), "无补充说明");
        appendCurrentUserLog(StrUtil.trim(emergencyLogRecordParam.getOperationType()),
                StrUtil.trim(emergencyLogRecordParam.getOperationName()), detail);
    }

    private void safeGenerateSignal() {
        try {
            reloadFacilityCatalog(false);
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
        List<EmergencyPoiResult> affectedPoiList = filterPoiInRange(centerLng, centerLat, radiusMeters);
        emergencySignalResult.setAffectedPoiCount(affectedPoiList.size());
        emergencySignalResult.setAffectedPoiList(affectedPoiList);
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
        List<EmergencyPoiResult> affectedPoiList = filterPoiInRange(centerLng, centerLat, radiusMeters);
        EmergencyScenarioStateResult emergencyScenarioStateResult = new EmergencyScenarioStateResult();
        emergencyScenarioStateResult.setCenterLng(round(centerLng, 6));
        emergencyScenarioStateResult.setCenterLat(round(centerLat, 6));
        emergencyScenarioStateResult.setRadiusMeters(round(radiusMeters, 2));
        emergencyScenarioStateResult.setTotalPoiCount(facilityCatalog.size());
        emergencyScenarioStateResult.setPoiCountInRange(affectedPoiList.size());
        emergencyScenarioStateResult.setAffectedPoiList(affectedPoiList);
        emergencyScenarioStateResult.setTotalFacilityStats(buildFacilityStatList(facilityCatalog));
        emergencyScenarioStateResult.setAffectedFacilityStats(buildFacilityStatList(affectedPoiList));
        emergencyScenarioStateResult.setSignalCounter(signalCounter.get());
        emergencyScenarioStateResult.setLatestSignal(copySignal(latestSignal));
        emergencyScenarioStateResult.setLeakPointList(leakPointDeque.stream().map(this::copyLeakPoint).collect(Collectors.toList()));
        return emergencyScenarioStateResult;
    }

    private EmergencyAiSuggestionResult buildAiSuggestion(EmergencyScenarioStateResult scenarioStateResult) {
        List<EmergencyPoiResult> affectedPoiList = ObjectUtil.isEmpty(scenarioStateResult.getAffectedPoiList())
                ? Collections.emptyList() : scenarioStateResult.getAffectedPoiList();
        List<EmergencyLeakPointResult> leakPointList = ObjectUtil.isEmpty(scenarioStateResult.getLeakPointList())
                ? Collections.emptyList() : scenarioStateResult.getLeakPointList();
        Map<String, Integer> typeCountMap = buildAffectedTypeCountMap(affectedPoiList);
        List<Map.Entry<String, Integer>> sortedTypeEntries = typeCountMap.entrySet().stream()
                .sorted((left, right) -> right.getValue().compareTo(left.getValue()))
                .collect(Collectors.toList());
        String warningLabel = resolveAiWarningLabel(scenarioStateResult.getRadiusMeters(), affectedPoiList.size(),
                scenarioStateResult.getLatestSignal());
        String topFacilitySummary = buildTopFacilitySummary(sortedTypeEntries);
        List<String> actionList = buildAiActionList(typeCountMap, leakPointList.size(), scenarioStateResult.getRadiusMeters(),
                scenarioStateResult.getLatestSignal());
        List<EmergencyRescueRouteResult> routeList = buildAiRescueRouteList(scenarioStateResult, affectedPoiList, leakPointList);

        EmergencyAiSuggestionResult result = new EmergencyAiSuggestionResult();
        result.setProviderName("模拟大模型推演引擎");
        result.setModeLabel("Prompt Copilot / Mock LLM");
        result.setRiskSummary(StrUtil.format("{} · 半径{}米 · 圈内设施{}个 · 漏水点{}个",
                warningLabel,
                round(ObjectUtil.defaultIfNull(scenarioStateResult.getRadiusMeters(), DEFAULT_RADIUS_METERS), 0),
                affectedPoiList.size(),
                leakPointList.size()));
        result.setSuggestion(buildAiSuggestionText(warningLabel, scenarioStateResult, topFacilitySummary, actionList, routeList));
        result.setActionList(actionList);
        result.setRouteList(routeList);
        result.setPromptTitle("GeoJSON / 空间计算 Prompt 辅助");
        result.setPromptTemplate(buildPromptTemplate(scenarioStateResult, sortedTypeEntries, actionList, warningLabel, routeList));
        result.setGeoJsonExample(buildGeoJsonExample(scenarioStateResult, affectedPoiList, leakPointList, warningLabel, routeList));
        result.setSpatialCodeExample(buildSpatialCodeExample(scenarioStateResult, routeList));
        result.setGeneratedTime(new Date());
        return result;
    }

    private List<EmergencyPoiResult> filterPoiInRange(double centerLng, double centerLat, double radiusMeters) {
        return facilityCatalog.stream().map(this::copyPoi).peek(emergencyPoiResult -> emergencyPoiResult
                        .setDistanceMeters(round(calculateDistanceMeters(centerLng, centerLat, emergencyPoiResult.getLng(),
                                emergencyPoiResult.getLat()), 2)))
                .filter(emergencyPoiResult -> emergencyPoiResult.getDistanceMeters() <= round(radiusMeters, 2))
                .sorted((left, right) -> left.getDistanceMeters().compareTo(right.getDistanceMeters()))
                .collect(Collectors.toList());
    }

    private void reloadFacilityCatalog(boolean failOnError) {
        try {
            List<EmergencyFacility> emergencyFacilityList = emergencyFacilityMapper.selectList(
                    new LambdaQueryWrapper<EmergencyFacility>()
                            .orderByAsc(EmergencyFacility::getSortCode)
                            .orderByAsc(EmergencyFacility::getName)
            );
            List<EmergencyPoiResult> latestFacilityCatalog = ObjectUtil.isEmpty(emergencyFacilityList)
                    ? Collections.emptyList()
                    : emergencyFacilityList.stream().map(this::copyFacilityToPoi).collect(Collectors.toList());
            if (failOnError && latestFacilityCatalog.isEmpty()) {
                throw new CommonException(FACILITY_DATA_MISSING_MESSAGE);
            }
            facilityCatalog.clear();
            facilityCatalog.addAll(latestFacilityCatalog);
        } catch (CommonException e) {
            throw e;
        } catch (Exception e) {
            log.error("load emergency facility catalog from db error", e);
            if (failOnError && facilityCatalog.isEmpty()) {
                throw new CommonException(FACILITY_DATA_MISSING_MESSAGE);
            }
        }
    }

    private EmergencyPoiResult copyFacilityToPoi(EmergencyFacility emergencyFacility) {
        EmergencyPoiResult emergencyPoiResult = new EmergencyPoiResult();
        emergencyPoiResult.setId(emergencyFacility.getId());
        emergencyPoiResult.setName(emergencyFacility.getName());
        emergencyPoiResult.setType(emergencyFacility.getType());
        emergencyPoiResult.setLng(round(ObjectUtil.defaultIfNull(emergencyFacility.getLng(), DEFAULT_CENTER_LNG), 6));
        emergencyPoiResult.setLat(round(ObjectUtil.defaultIfNull(emergencyFacility.getLat(), DEFAULT_CENTER_LAT), 6));
        return emergencyPoiResult;
    }

    private List<EmergencyFacilityStatResult> buildFacilityStatList(List<EmergencyPoiResult> poiList) {
        Map<String, Integer> typeCountMap = new LinkedHashMap<>();
        facilityCatalog.stream()
                .map(EmergencyPoiResult::getType)
                .filter(StrUtil::isNotBlank)
                .forEach(type -> typeCountMap.putIfAbsent(type, 0));
        for (EmergencyPoiResult emergencyPoiResult : poiList) {
            if (ObjectUtil.isEmpty(emergencyPoiResult) || StrUtil.isBlank(emergencyPoiResult.getType())) {
                continue;
            }
            typeCountMap.merge(emergencyPoiResult.getType(), 1, Integer::sum);
        }
        return typeCountMap.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> {
                    EmergencyFacilityStatResult emergencyFacilityStatResult = new EmergencyFacilityStatResult();
                    emergencyFacilityStatResult.setType(entry.getKey());
                    emergencyFacilityStatResult.setCount(entry.getValue());
                    return emergencyFacilityStatResult;
                })
                .collect(Collectors.toList());
    }

    private void initializeLeakPoints() {
        leakPointDeque.clear();
        for (int index = 0; index < INITIAL_LEAK_POINT_SIZE; index++) {
            leakPointDeque.addLast(createInitialLeakPoint(DEFAULT_CENTER_LNG, DEFAULT_CENTER_LAT, DEFAULT_RADIUS_METERS, index));
        }
    }

    private EmergencyLeakPointResult createInitialLeakPoint(double centerLng, double centerLat, double radiusMeters, int index) {
        String leakPointName = String.format("漏水点-预置%02d", index + 1);
        EmergencyPoiResult anchorPoi = selectLeakAnchorPoi(centerLng, centerLat, radiusMeters, index);
        if (ObjectUtil.isNotEmpty(anchorPoi)) {
            return createLeakPointAroundPoi(anchorPoi, centerLng, centerLat, leakPointName, index);
        }
        return createFallbackLeakPoint(centerLng, centerLat, leakPointName, index);
    }

    private EmergencyLeakPointResult createLeakPoint(double centerLng, double centerLat, double radiusMeters, Long signalNo) {
        String leakPointName = String.format("漏水点-%03d", signalNo);
        EmergencyPoiResult anchorPoi = selectLeakAnchorPoi(centerLng, centerLat, radiusMeters, signalNo);
        if (ObjectUtil.isNotEmpty(anchorPoi)) {
            return createLeakPointAroundPoi(anchorPoi, centerLng, centerLat, leakPointName, signalNo);
        }
        return createFallbackLeakPoint(centerLng, centerLat, leakPointName, signalNo);
    }

    private EmergencyPoiResult selectLeakAnchorPoi(double centerLng, double centerLat, double radiusMeters, long seed) {
        double anchorRadiusMeters = Math.max(radiusMeters * LEAK_POINT_ANCHOR_RADIUS_RATIO, 320D);
        List<EmergencyPoiResult> candidatePoiList = filterPoiInRange(centerLng, centerLat, anchorRadiusMeters);
        if (candidatePoiList.isEmpty()) {
            return null;
        }
        int windowSize = Math.min(candidatePoiList.size(), 18);
        int selectedIndex = (int) Math.floorMod(seed * 5L + 3L, windowSize);
        return candidatePoiList.get(selectedIndex);
    }

    private EmergencyLeakPointResult createLeakPointAroundPoi(EmergencyPoiResult anchorPoi, double centerLng, double centerLat,
                                                              String leakPointName, long seed) {
        double anchorLng = anchorPoi.getLng();
        double anchorLat = anchorPoi.getLat();
        double toCenterEastMeters = (centerLng - anchorLng) * 111320D * Math.cos(Math.toRadians(anchorLat));
        double toCenterNorthMeters = (centerLat - anchorLat) * 110540D;
        double vectorLength = Math.sqrt(toCenterEastMeters * toCenterEastMeters + toCenterNorthMeters * toCenterNorthMeters);
        double inwardOffsetMeters = LEAK_POINT_INWARD_OFFSET_BASE_METERS
                + Math.floorMod(seed * 3L + 1L, 5L) * (LEAK_POINT_INWARD_OFFSET_SPAN_METERS / 4D);
        double lateralOffsetMeters = LEAK_POINT_LATERAL_OFFSET_BASE_METERS
                + Math.floorMod(seed * 5L + 2L, 6L) * (LEAK_POINT_LATERAL_OFFSET_SPAN_METERS / 5D);

        double inwardEastMeters;
        double inwardNorthMeters;
        double lateralEastMeters;
        double lateralNorthMeters;
        if (vectorLength > 1D) {
            inwardEastMeters = toCenterEastMeters / vectorLength * inwardOffsetMeters;
            inwardNorthMeters = toCenterNorthMeters / vectorLength * inwardOffsetMeters;
            double lateralDirection = Math.floorMod(seed, 2L) == 0L ? 1D : -1D;
            lateralEastMeters = -toCenterNorthMeters / vectorLength * lateralOffsetMeters * lateralDirection;
            lateralNorthMeters = toCenterEastMeters / vectorLength * lateralOffsetMeters * lateralDirection;
        } else {
            double fallbackDirection = Math.floorMod(seed, 2L) == 0L ? 1D : -1D;
            inwardEastMeters = -6D;
            inwardNorthMeters = 10D;
            lateralEastMeters = 8D * fallbackDirection;
            lateralNorthMeters = 4D * fallbackDirection;
        }

        return buildLeakPoint(anchorLng, anchorLat, inwardEastMeters + lateralEastMeters,
                inwardNorthMeters + lateralNorthMeters, leakPointName);
    }

    private EmergencyLeakPointResult createFallbackLeakPoint(double centerLng, double centerLat, String leakPointName, long seed) {
        double[] eastOffsetArray = {-110D, -70D, -35D};
        double[] northOffsetArray = {140D, 90D, 180D};
        int fallbackIndex = (int) Math.floorMod(seed, eastOffsetArray.length);
        return buildLeakPoint(centerLng, centerLat, eastOffsetArray[fallbackIndex], northOffsetArray[fallbackIndex], leakPointName);
    }

    private EmergencyLeakPointResult buildLeakPoint(double anchorLng, double anchorLat, double eastMeters, double northMeters,
                                                    String leakPointName) {
        EmergencyLeakPointResult emergencyLeakPointResult = new EmergencyLeakPointResult();
        emergencyLeakPointResult.setId(IdUtil.fastSimpleUUID());
        emergencyLeakPointResult.setName(leakPointName);
        emergencyLeakPointResult.setLng(round(offsetLng(anchorLng, anchorLat, eastMeters), 6));
        emergencyLeakPointResult.setLat(round(offsetLat(anchorLat, northMeters), 6));
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
        result.setAffectedPoiList(ObjectUtil.isEmpty(emergencySignalResult.getAffectedPoiList()) ? Collections.emptyList()
                : emergencySignalResult.getAffectedPoiList().stream().map(this::copyPoi).collect(Collectors.toList()));
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

    private Map<String, Integer> buildAffectedTypeCountMap(List<EmergencyPoiResult> affectedPoiList) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (EmergencyPoiResult emergencyPoiResult : affectedPoiList) {
            if (ObjectUtil.isEmpty(emergencyPoiResult) || StrUtil.isBlank(emergencyPoiResult.getType())) {
                continue;
            }
            result.merge(emergencyPoiResult.getType(), 1, Integer::sum);
        }
        return result;
    }

    private String buildTopFacilitySummary(List<Map.Entry<String, Integer>> sortedTypeEntries) {
        if (ObjectUtil.isEmpty(sortedTypeEntries)) {
            return "受灾设施分布尚不明显";
        }
        return sortedTypeEntries.stream()
                .limit(3)
                .map(item -> item.getKey() + item.getValue() + "个")
                .collect(Collectors.joining("、"));
    }

    private List<String> buildAiActionList(Map<String, Integer> typeCountMap, int leakPointCount, Double radiusMeters,
                                           EmergencySignalResult emergencySignalResult) {
        List<String> result = new ArrayList<>();
        int hospitalCount = typeCountMap.getOrDefault("医院", 0);
        int schoolCount = typeCountMap.getOrDefault("学校", 0);
        int shelterCount = typeCountMap.getOrDefault("避险点", 0);
        int drainageCount = typeCountMap.getOrDefault("排涝设施", 0);
        int transportCount = typeCountMap.getOrDefault("交通枢纽", 0);

        if (hospitalCount + schoolCount > 0) {
            result.add(StrUtil.format("优先保护{}处医院和{}处学校，先行排查低洼出入口并预置转运车辆。", hospitalCount, schoolCount));
        }
        if (leakPointCount > 0 || drainageCount > 0) {
            result.add(StrUtil.format("围绕{}个漏水点和{}处排涝设施建立近场排水单元，15分钟内完成泵车与巡检队联动。", leakPointCount, drainageCount));
        }
        if (shelterCount > 0) {
            result.add(StrUtil.format("联动{}处避险点，按“学校/社区/医院”优先顺序规划疏散导流路线。", shelterCount));
        }
        if (transportCount > 0 || NumberUtil.compare(ObjectUtil.defaultIfNull(radiusMeters, DEFAULT_RADIUS_METERS), 1800D) >= 0) {
            result.add(StrUtil.format("对{}处交通枢纽执行分级交通管控，保留至少2条应急救援通道。", transportCount));
        }
        if (ObjectUtil.isNotEmpty(emergencySignalResult) && "NEW_LEAK_POINT".equals(emergencySignalResult.getSignalType())) {
            result.add(StrUtil.format("针对{}触发的新增险情，追加移动照明、无人机巡检与周边积水深度复测。",
                    ObjectUtil.isNotEmpty(emergencySignalResult.getLeakPoint())
                            ? emergencySignalResult.getLeakPoint().getName() : "本轮漏水点"));
        }
        if (result.isEmpty()) {
            result.add("建议维持当前监测半径，继续滚动核验圈内设施受损等级与人员疏散状态。");
        }
        return result.stream().limit(4).collect(Collectors.toList());
    }

    private String buildAiSuggestionText(String warningLabel, EmergencyScenarioStateResult scenarioStateResult,
                                         String topFacilitySummary, List<String> actionList,
                                         List<EmergencyRescueRouteResult> routeList) {
        String actionSummary = actionList.stream().limit(2).collect(Collectors.joining("；"));
        String routeSummary = buildAiRouteSummary(routeList);
        return StrUtil.format("模拟大模型研判：当前{}，以({}, {})为核心、半径{}米范围内共有{}个受灾设施，主要集中在{}。建议{}。{}",
                warningLabel,
                round(ObjectUtil.defaultIfNull(scenarioStateResult.getCenterLng(), DEFAULT_CENTER_LNG), 6),
                round(ObjectUtil.defaultIfNull(scenarioStateResult.getCenterLat(), DEFAULT_CENTER_LAT), 6),
                round(ObjectUtil.defaultIfNull(scenarioStateResult.getRadiusMeters(), DEFAULT_RADIUS_METERS), 0),
                ObjectUtil.defaultIfNull(scenarioStateResult.getPoiCountInRange(), 0),
                topFacilitySummary,
                StrUtil.blankToDefault(actionSummary, "继续加强监测与资源前置"),
                routeSummary);
    }

    private String buildPromptTemplate(EmergencyScenarioStateResult scenarioStateResult,
                                       List<Map.Entry<String, Integer>> sortedTypeEntries,
                                       List<String> actionList,
                                       String warningLabel,
                                       List<EmergencyRescueRouteResult> routeList) {
        String facilitySummary = ObjectUtil.isEmpty(sortedTypeEntries)
                ? "无明显受灾设施类型集中"
                : sortedTypeEntries.stream()
                .limit(6)
                .map(item -> StrUtil.format("{}: {}个", item.getKey(), item.getValue()))
                .collect(Collectors.joining("\n"));
        String actionSummary = ObjectUtil.isEmpty(actionList)
                ? "- 继续监测灾情演变"
                : actionList.stream().map(item -> "- " + item).collect(Collectors.joining("\n"));
        String leakSummary = ObjectUtil.isEmpty(scenarioStateResult.getLeakPointList())
                ? "当前无新增漏水点"
                : scenarioStateResult.getLeakPointList().stream()
                .limit(3)
                .map(item -> StrUtil.format("{}({},{})", item.getName(), item.getLng(), item.getLat()))
                .collect(Collectors.joining("、"));
        String routeSummary = ObjectUtil.isEmpty(routeList)
                ? "- 暂无路线输出"
                : routeList.stream()
                .map(item -> StrUtil.format("- {}: {} -> {}，预计{}分钟",
                        item.getRouteName(),
                        resolveRoutePointName(item, 0, "起点"),
                        resolveRoutePointName(item, -1, "终点"),
                        ObjectUtil.defaultIfNull(item.getEstimatedMinutes(), 0)))
                .collect(Collectors.joining("\n"));
        return StrUtil.format(
                "你是城市内涝应急 GIS 助手，请基于以下灾情态势输出救援方案、GeoJSON 结构建议和空间计算代码。\n\n" +
                        "【当前态势】\n" +
                        "- 预警等级: {}\n" +
                        "- 灾情中心: ({}, {})\n" +
                        "- 受灾半径: {} 米\n" +
                        "- 圈内设施数: {} 个\n" +
                        "- 漏水点: {}\n" +
                        "- 受灾设施类型统计:\n{}\n\n" +
                        "【希望模型完成的任务】\n" +
                        "1. 输出 3 条分优先级的救援调度建议。\n" +
                        "2. 生成一个符合 GeoJSON FeatureCollection 规范的积水区 / 设施点位示例结构。\n" +
                        "3. 规划 3 条可执行的救援路线，输出起点、途经点和终点。\n" +
                        "4. 给出一段 JavaScript 空间计算代码，包含圆形范围筛选、点位避让、多边形面积估算和路线折线构造。\n" +
                        "5. 输出格式请使用 JSON，字段包含 plan、routeList、geojson、spatialCode、riskNotes。\n\n" +
                        "【当前已生成的处置建议】\n{}\n",
                warningLabel,
                round(ObjectUtil.defaultIfNull(scenarioStateResult.getCenterLng(), DEFAULT_CENTER_LNG), 6),
                round(ObjectUtil.defaultIfNull(scenarioStateResult.getCenterLat(), DEFAULT_CENTER_LAT), 6),
                round(ObjectUtil.defaultIfNull(scenarioStateResult.getRadiusMeters(), DEFAULT_RADIUS_METERS), 0),
                ObjectUtil.defaultIfNull(scenarioStateResult.getPoiCountInRange(), 0),
                leakSummary,
                facilitySummary,
                actionSummary + "\n\n【当前生成的救援路线】\n" + routeSummary
        );
    }

    private String buildGeoJsonExample(EmergencyScenarioStateResult scenarioStateResult,
                                       List<EmergencyPoiResult> affectedPoiList,
                                       List<EmergencyLeakPointResult> leakPointList,
                                       String warningLabel,
                                       List<EmergencyRescueRouteResult> routeList) {
        Map<String, Object> featureCollection = new LinkedHashMap<>();
        featureCollection.put("type", "FeatureCollection");
        List<Map<String, Object>> featureList = new ArrayList<>();
        featureList.add(buildDisasterCenterFeature(scenarioStateResult, warningLabel));
        featureList.add(buildFloodRangeFeature(scenarioStateResult, warningLabel));
        affectedPoiList.stream().limit(8).forEach(item -> featureList.add(buildPoiFeature(item)));
        leakPointList.stream().limit(3).forEach(item -> featureList.add(buildLeakPointFeature(item)));
        routeList.forEach(item -> {
            Map<String, Object> feature = buildRouteFeature(item);
            if (ObjectUtil.isNotEmpty(feature)) {
                featureList.add(feature);
            }
        });
        featureCollection.put("features", featureList);
        return JSONUtil.toJsonPrettyStr(featureCollection);
    }

    private Map<String, Object> buildDisasterCenterFeature(EmergencyScenarioStateResult scenarioStateResult, String warningLabel) {
        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("type", "Feature");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("kind", "disaster-center");
        properties.put("warningLevel", warningLabel);
        properties.put("radiusMeters", round(ObjectUtil.defaultIfNull(scenarioStateResult.getRadiusMeters(), DEFAULT_RADIUS_METERS), 0));
        properties.put("affectedPoiCount", ObjectUtil.defaultIfNull(scenarioStateResult.getPoiCountInRange(), 0));
        feature.put("properties", properties);
        feature.put("geometry", buildPointGeometry(
                ObjectUtil.defaultIfNull(scenarioStateResult.getCenterLng(), DEFAULT_CENTER_LNG),
                ObjectUtil.defaultIfNull(scenarioStateResult.getCenterLat(), DEFAULT_CENTER_LAT)));
        return feature;
    }

    private Map<String, Object> buildFloodRangeFeature(EmergencyScenarioStateResult scenarioStateResult, String warningLabel) {
        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("type", "Feature");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("kind", "flood-range");
        properties.put("warningLevel", warningLabel);
        properties.put("styleKey", resolveAiWarningStyleKey(warningLabel));
        feature.put("properties", properties);

        Map<String, Object> geometry = new LinkedHashMap<>();
        geometry.put("type", "Polygon");
        List<List<List<Double>>> coordinates = new ArrayList<>();
        coordinates.add(buildCircleCoordinates(
                ObjectUtil.defaultIfNull(scenarioStateResult.getCenterLng(), DEFAULT_CENTER_LNG),
                ObjectUtil.defaultIfNull(scenarioStateResult.getCenterLat(), DEFAULT_CENTER_LAT),
                ObjectUtil.defaultIfNull(scenarioStateResult.getRadiusMeters(), DEFAULT_RADIUS_METERS),
                28));
        geometry.put("coordinates", coordinates);
        feature.put("geometry", geometry);
        return feature;
    }

    private Map<String, Object> buildPoiFeature(EmergencyPoiResult emergencyPoiResult) {
        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("type", "Feature");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", emergencyPoiResult.getId());
        properties.put("name", emergencyPoiResult.getName());
        properties.put("type", emergencyPoiResult.getType());
        properties.put("distanceMeters", ObjectUtil.defaultIfNull(emergencyPoiResult.getDistanceMeters(), 0D));
        feature.put("properties", properties);
        feature.put("geometry", buildPointGeometry(emergencyPoiResult.getLng(), emergencyPoiResult.getLat()));
        return feature;
    }

    private Map<String, Object> buildLeakPointFeature(EmergencyLeakPointResult emergencyLeakPointResult) {
        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("type", "Feature");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("kind", "leak-point");
        properties.put("name", emergencyLeakPointResult.getName());
        properties.put("detectedTime", emergencyLeakPointResult.getDetectedTime());
        feature.put("properties", properties);
        feature.put("geometry", buildPointGeometry(emergencyLeakPointResult.getLng(), emergencyLeakPointResult.getLat()));
        return feature;
    }

    private Map<String, Object> buildRouteFeature(EmergencyRescueRouteResult emergencyRescueRouteResult) {
        if (ObjectUtil.isEmpty(emergencyRescueRouteResult) || ObjectUtil.isEmpty(emergencyRescueRouteResult.getPointList())
                || emergencyRescueRouteResult.getPointList().size() < 2) {
            return null;
        }
        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("type", "Feature");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("kind", "rescue-route");
        properties.put("routeId", emergencyRescueRouteResult.getRouteId());
        properties.put("routeName", emergencyRescueRouteResult.getRouteName());
        properties.put("routeType", emergencyRescueRouteResult.getRouteType());
        properties.put("lineColor", emergencyRescueRouteResult.getLineColor());
        feature.put("properties", properties);

        Map<String, Object> geometry = new LinkedHashMap<>();
        geometry.put("type", "LineString");
        List<List<Double>> coordinateList = emergencyRescueRouteResult.getPointList().stream()
                .filter(item -> ObjectUtil.isNotEmpty(item) && ObjectUtil.isNotNull(item.getLng()) && ObjectUtil.isNotNull(item.getLat()))
                .map(item -> {
                    List<Double> coordinate = new ArrayList<>();
                    coordinate.add(round(item.getLng(), 6));
                    coordinate.add(round(item.getLat(), 6));
                    return coordinate;
                })
                .collect(Collectors.toList());
        if (coordinateList.size() < 2) {
            return null;
        }
        geometry.put("coordinates", coordinateList);
        feature.put("geometry", geometry);
        return feature;
    }

    private Map<String, Object> buildPointGeometry(Double lng, Double lat) {
        Map<String, Object> geometry = new LinkedHashMap<>();
        geometry.put("type", "Point");
        List<Double> coordinates = new ArrayList<>();
        coordinates.add(round(ObjectUtil.defaultIfNull(lng, DEFAULT_CENTER_LNG), 6));
        coordinates.add(round(ObjectUtil.defaultIfNull(lat, DEFAULT_CENTER_LAT), 6));
        geometry.put("coordinates", coordinates);
        return geometry;
    }

    private List<List<Double>> buildCircleCoordinates(double centerLng, double centerLat, double radiusMeters, int segmentCount) {
        List<List<Double>> coordinates = new ArrayList<>();
        for (int index = 0; index <= segmentCount; index++) {
            double angle = Math.toRadians((360D / segmentCount) * index);
            double eastMeters = Math.cos(angle) * radiusMeters;
            double northMeters = Math.sin(angle) * radiusMeters;
            List<Double> point = new ArrayList<>();
            point.add(round(offsetLng(centerLng, centerLat, eastMeters), 6));
            point.add(round(offsetLat(centerLat, northMeters), 6));
            coordinates.add(point);
        }
        return coordinates;
    }

    private String buildSpatialCodeExample(EmergencyScenarioStateResult scenarioStateResult,
                                           List<EmergencyRescueRouteResult> routeList) {
        double centerLng = round(ObjectUtil.defaultIfNull(scenarioStateResult.getCenterLng(), DEFAULT_CENTER_LNG), 6);
        double centerLat = round(ObjectUtil.defaultIfNull(scenarioStateResult.getCenterLat(), DEFAULT_CENTER_LAT), 6);
        double radiusMeters = round(ObjectUtil.defaultIfNull(scenarioStateResult.getRadiusMeters(), DEFAULT_RADIUS_METERS), 0);
        String routeListComment = ObjectUtil.isEmpty(routeList)
                ? "// 当前暂无路线结果，调用 buildRescueRoute 时可传入起终点生成折线"
                : "// 当前路线示例: " + routeList.stream().map(EmergencyRescueRouteResult::getRouteName).collect(Collectors.joining(" / "));
        return StrUtil.format(
                "const center = [{}, {}];\n" +
                        "const radiusMeters = {};\n\n" +
                        "function haversineMeters([lng1, lat1], [lng2, lat2]) {\n" +
                        "  const toRad = (value) => (value * Math.PI) / 180;\n" +
                        "  const earthRadius = 6371000;\n" +
                        "  const dLat = toRad(lat2 - lat1);\n" +
                        "  const dLng = toRad(lng2 - lng1);\n" +
                        "  const a = Math.sin(dLat / 2) ** 2 + Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;\n" +
                        "  return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));\n" +
                        "}\n\n" +
                        "export function filterAffectedPoi(poiList) {\n" +
                        "  return poiList\n" +
                        "    .map((poi) => ({ ...poi, distanceMeters: haversineMeters(center, [poi.lng, poi.lat]) }))\n" +
                        "    .filter((poi) => poi.distanceMeters <= radiusMeters)\n" +
                        "    .sort((left, right) => left.distanceMeters - right.distanceMeters);\n" +
                        "}\n\n" +
                        "export function spreadClusteredPoi(poiList, minGapMeters = 60) {\n" +
                        "  const meterToLng = (meters, lat) => meters / (111320 * Math.cos((lat * Math.PI) / 180));\n" +
                        "  const meterToLat = (meters) => meters / 110540;\n" +
                        "  return poiList.map((poi, index) => {\n" +
                        "    const angle = ((index % 12) / 12) * Math.PI * 2;\n" +
                        "    const ring = Math.floor(index / 12) + 1;\n" +
                        "    const offsetMeters = ring * minGapMeters;\n" +
                        "    return {\n" +
                        "      ...poi,\n" +
                        "      renderLng: poi.lng + meterToLng(Math.cos(angle) * offsetMeters, poi.lat),\n" +
                        "      renderLat: poi.lat + meterToLat(Math.sin(angle) * offsetMeters)\n" +
                        "    };\n" +
                        "  });\n" +
                        "}\n\n" +
                        "export function estimatePolygonAreaSqm(ring) {\n" +
                        "  if (!Array.isArray(ring) || ring.length < 3) return 0;\n" +
                        "  const meters = ring.map(([lng, lat]) => ([lng * 111320 * Math.cos((lat * Math.PI) / 180), lat * 110540]));\n" +
                        "  let area = 0;\n" +
                        "  for (let i = 0; i < meters.length; i += 1) {\n" +
                        "    const [x1, y1] = meters[i];\n" +
                        "    const [x2, y2] = meters[(i + 1) % meters.length];\n" +
                        "    area += x1 * y2 - x2 * y1;\n" +
                        "  }\n" +
                        "  return Math.abs(area / 2);\n" +
                        "}\n\n" +
                        "{}\n" +
                        "export function buildRescueRoute(start, end, bendMeters = 90) {\n" +
                        "  const meterToLng = (meters, lat) => meters / (111320 * Math.cos((lat * Math.PI) / 180));\n" +
                        "  const meterToLat = (meters) => meters / 110540;\n" +
                        "  const dx = end[0] - start[0];\n" +
                        "  const dy = end[1] - start[1];\n" +
                        "  const norm = Math.sqrt(dx * dx + dy * dy) || 1;\n" +
                        "  const nx = (-dy / norm) * bendMeters;\n" +
                        "  const ny = (dx / norm) * bendMeters;\n" +
                        "  const midLng = (start[0] + end[0]) / 2;\n" +
                        "  const midLat = (start[1] + end[1]) / 2;\n" +
                        "  return [\n" +
                        "    start,\n" +
                        "    [midLng + meterToLng(nx, midLat), midLat + meterToLat(ny)],\n" +
                        "    end\n" +
                        "  ];\n" +
                        "}\n",
                centerLng, centerLat, radiusMeters, routeListComment
        );
    }

    private List<EmergencyRescueRouteResult> buildAiRescueRouteList(EmergencyScenarioStateResult scenarioStateResult,
                                                                    List<EmergencyPoiResult> affectedPoiList,
                                                                    List<EmergencyLeakPointResult> leakPointList) {
        List<EmergencyRescueRouteResult> result = new ArrayList<>();
        double[] disasterCenter = resolveAiDisasterCenterCoordinate(scenarioStateResult, affectedPoiList, leakPointList);
        EmergencyLeakPointResult nearestLeakPoint = findNearestLeakPoint(leakPointList, disasterCenter[0], disasterCenter[1]);

        EmergencyPoiResult drainageOrigin = findNearestFacilityByTypes(facilityCatalog, disasterCenter[0], disasterCenter[1], null,
                "排涝设施", "消防设施", "应急指挥");
        if (ObjectUtil.isNotEmpty(drainageOrigin)) {
            String targetName = "灾情中心";
            double targetLng = disasterCenter[0];
            double targetLat = disasterCenter[1];
            String description = "优先打通近场排涝与泵车投放通道";
            if (ObjectUtil.isNotEmpty(nearestLeakPoint)) {
                targetName = nearestLeakPoint.getName();
                targetLng = ObjectUtil.defaultIfNull(nearestLeakPoint.getLng(), disasterCenter[0]);
                targetLat = ObjectUtil.defaultIfNull(nearestLeakPoint.getLat(), disasterCenter[1]);
                description = "排涝抢险组沿最近通道进入漏水点周边处置";
            }
            EmergencyRescueRouteResult drainageRoute = buildRescueRoute(
                    "排涝抢险路线",
                    "drainage",
                    "#38bdf8",
                    1,
                    drainageOrigin.getName(),
                    drainageOrigin.getLng(),
                    drainageOrigin.getLat(),
                    targetName,
                    targetLng,
                    targetLat,
                    disasterCenter,
                    description
            );
            if (ObjectUtil.isNotEmpty(drainageRoute)) {
                result.add(drainageRoute);
            }
        }

        EmergencyPoiResult medicalTarget = findPriorityAffectedFacility(affectedPoiList, disasterCenter[0], disasterCenter[1],
                null, "医院", "学校", "社区服务", "交通枢纽", "物资仓储");
        EmergencyPoiResult medicalOrigin = findNearestFacilityByTypes(facilityCatalog,
                ObjectUtil.isNotEmpty(medicalTarget) ? medicalTarget.getLng() : disasterCenter[0],
                ObjectUtil.isNotEmpty(medicalTarget) ? medicalTarget.getLat() : disasterCenter[1],
                ObjectUtil.isNotEmpty(medicalTarget) ? medicalTarget.getId() : null,
                "医院", "应急指挥", "消防设施");
        if (ObjectUtil.isNotEmpty(medicalOrigin)) {
            EmergencyRescueRouteResult medicalRoute = buildRescueRoute(
                    "医疗救援路线",
                    "medical",
                    "#fb7185",
                    2,
                    medicalOrigin.getName(),
                    medicalOrigin.getLng(),
                    medicalOrigin.getLat(),
                    ObjectUtil.isNotEmpty(medicalTarget) ? medicalTarget.getName() : "灾情中心",
                    ObjectUtil.isNotEmpty(medicalTarget) ? medicalTarget.getLng() : disasterCenter[0],
                    ObjectUtil.isNotEmpty(medicalTarget) ? medicalTarget.getLat() : disasterCenter[1],
                    disasterCenter,
                    "医疗救援组优先进入重点受灾设施，建立现场急救与转运节点"
            );
            if (ObjectUtil.isNotEmpty(medicalRoute)) {
                result.add(medicalRoute);
            }
        }

        EmergencyPoiResult evacuationOrigin = findPriorityAffectedFacility(affectedPoiList, disasterCenter[0], disasterCenter[1],
                null, "学校", "社区服务", "医院", "交通枢纽");
        if (ObjectUtil.isNotEmpty(evacuationOrigin)) {
            EmergencyPoiResult evacuationTarget = findNearestFacilityByTypes(facilityCatalog, evacuationOrigin.getLng(),
                    evacuationOrigin.getLat(), evacuationOrigin.getId(), "避险点", "应急指挥");
            if (ObjectUtil.isNotEmpty(evacuationTarget)) {
                EmergencyRescueRouteResult evacuationRoute = buildRescueRoute(
                        "人员转运路线",
                        "evacuation",
                        "#22c55e",
                        3,
                        evacuationOrigin.getName(),
                        evacuationOrigin.getLng(),
                        evacuationOrigin.getLat(),
                        evacuationTarget.getName(),
                        evacuationTarget.getLng(),
                        evacuationTarget.getLat(),
                        disasterCenter,
                        "疏散转运组按最近避险通道组织人员分批转运"
                );
                if (ObjectUtil.isNotEmpty(evacuationRoute)) {
                    result.add(evacuationRoute);
                }
            }
        }

        if (result.isEmpty()) {
            EmergencyPoiResult commandOrigin = findNearestFacilityByTypes(facilityCatalog, disasterCenter[0], disasterCenter[1], null,
                    "应急指挥", "消防设施");
            if (ObjectUtil.isNotEmpty(commandOrigin)) {
                EmergencyRescueRouteResult dispatchRoute = buildRescueRoute(
                        "指挥调度路线",
                        "dispatch",
                        "#fbbf24",
                        1,
                        commandOrigin.getName(),
                        commandOrigin.getLng(),
                        commandOrigin.getLat(),
                        "灾情中心",
                        disasterCenter[0],
                        disasterCenter[1],
                        disasterCenter,
                        "指挥调度组沿主通道进入灾情中心布设前置指挥点"
                );
                if (ObjectUtil.isNotEmpty(dispatchRoute)) {
                    result.add(dispatchRoute);
                }
            }
        }
        return result.stream().limit(3).collect(Collectors.toList());
    }

    private double[] resolveAiDisasterCenterCoordinate(EmergencyScenarioStateResult scenarioStateResult,
                                                       List<EmergencyPoiResult> affectedPoiList,
                                                       List<EmergencyLeakPointResult> leakPointList) {
        double fallbackLng = ObjectUtil.defaultIfNull(scenarioStateResult.getCenterLng(), DEFAULT_CENTER_LNG);
        double fallbackLat = ObjectUtil.defaultIfNull(scenarioStateResult.getCenterLat(), DEFAULT_CENTER_LAT);
        double lngWeightedTotal = 0D;
        double latWeightedTotal = 0D;
        double weightTotal = 0D;

        for (EmergencyLeakPointResult emergencyLeakPointResult : leakPointList) {
            if (ObjectUtil.isEmpty(emergencyLeakPointResult) || ObjectUtil.isNull(emergencyLeakPointResult.getLng())
                    || ObjectUtil.isNull(emergencyLeakPointResult.getLat())) {
                continue;
            }
            double weight = 1.65D;
            lngWeightedTotal += emergencyLeakPointResult.getLng() * weight;
            latWeightedTotal += emergencyLeakPointResult.getLat() * weight;
            weightTotal += weight;
        }

        for (EmergencyPoiResult emergencyPoiResult : affectedPoiList) {
            if (ObjectUtil.isEmpty(emergencyPoiResult) || ObjectUtil.isNull(emergencyPoiResult.getLng())
                    || ObjectUtil.isNull(emergencyPoiResult.getLat())) {
                continue;
            }
            double weight = resolveAffectedPoiWeight(emergencyPoiResult.getType());
            lngWeightedTotal += emergencyPoiResult.getLng() * weight;
            latWeightedTotal += emergencyPoiResult.getLat() * weight;
            weightTotal += weight;
        }

        if (NumberUtil.compare(weightTotal, 0D) <= 0) {
            return new double[]{fallbackLng, fallbackLat};
        }
        return new double[]{
                round(lngWeightedTotal / weightTotal, 6),
                round(latWeightedTotal / weightTotal, 6)
        };
    }

    private double resolveAffectedPoiWeight(String type) {
        if (StrUtil.equalsAny(type, "医院", "学校", "交通枢纽")) {
            return 1.35D;
        }
        if (StrUtil.equalsAny(type, "排涝设施", "消防设施", "应急指挥")) {
            return 1.18D;
        }
        return 1D;
    }

    private EmergencyPoiResult findNearestFacilityByTypes(List<EmergencyPoiResult> sourceList, double referenceLng, double referenceLat,
                                                          String excludeId, String... typeArray) {
        if (ObjectUtil.isEmpty(sourceList) || ObjectUtil.isEmpty(typeArray)) {
            return null;
        }
        List<String> typeList = Arrays.asList(typeArray);
        return sourceList.stream()
                .filter(item -> ObjectUtil.isNotEmpty(item) && ObjectUtil.isNotNull(item.getLng()) && ObjectUtil.isNotNull(item.getLat()))
                .filter(item -> typeList.contains(item.getType()))
                .filter(item -> StrUtil.isBlank(excludeId) || !StrUtil.equals(excludeId, item.getId()))
                .min((left, right) -> Double.compare(
                        calculateDistanceMeters(referenceLng, referenceLat, left.getLng(), left.getLat()),
                        calculateDistanceMeters(referenceLng, referenceLat, right.getLng(), right.getLat())))
                .map(this::copyPoi)
                .orElse(null);
    }

    private EmergencyPoiResult findPriorityAffectedFacility(List<EmergencyPoiResult> affectedPoiList, double referenceLng,
                                                            double referenceLat, String excludeId, String... typeArray) {
        if (ObjectUtil.isEmpty(affectedPoiList)) {
            return null;
        }
        for (String type : typeArray) {
            EmergencyPoiResult matchedPoi = affectedPoiList.stream()
                    .filter(item -> ObjectUtil.isNotEmpty(item) && StrUtil.equals(type, item.getType()))
                    .filter(item -> StrUtil.isBlank(excludeId) || !StrUtil.equals(excludeId, item.getId()))
                    .min((left, right) -> Double.compare(
                            calculateDistanceMeters(referenceLng, referenceLat, left.getLng(), left.getLat()),
                            calculateDistanceMeters(referenceLng, referenceLat, right.getLng(), right.getLat())))
                    .map(this::copyPoi)
                    .orElse(null);
            if (ObjectUtil.isNotEmpty(matchedPoi)) {
                return matchedPoi;
            }
        }
        return affectedPoiList.stream()
                .filter(item -> ObjectUtil.isNotEmpty(item) && ObjectUtil.isNotNull(item.getLng()) && ObjectUtil.isNotNull(item.getLat()))
                .filter(item -> StrUtil.isBlank(excludeId) || !StrUtil.equals(excludeId, item.getId()))
                .min((left, right) -> Double.compare(
                        calculateDistanceMeters(referenceLng, referenceLat, left.getLng(), left.getLat()),
                        calculateDistanceMeters(referenceLng, referenceLat, right.getLng(), right.getLat())))
                .map(this::copyPoi)
                .orElse(null);
    }

    private EmergencyLeakPointResult findNearestLeakPoint(List<EmergencyLeakPointResult> leakPointList, double referenceLng,
                                                          double referenceLat) {
        if (ObjectUtil.isEmpty(leakPointList)) {
            return null;
        }
        return leakPointList.stream()
                .filter(item -> ObjectUtil.isNotEmpty(item) && ObjectUtil.isNotNull(item.getLng()) && ObjectUtil.isNotNull(item.getLat()))
                .min((left, right) -> Double.compare(
                        calculateDistanceMeters(referenceLng, referenceLat, left.getLng(), left.getLat()),
                        calculateDistanceMeters(referenceLng, referenceLat, right.getLng(), right.getLat())))
                .map(this::copyLeakPoint)
                .orElse(null);
    }

    private EmergencyRescueRouteResult buildRescueRoute(String routeName, String routeType, String lineColor, int priorityLevel,
                                                        String startName, Double startLng, Double startLat,
                                                        String endName, Double endLng, Double endLat,
                                                        double[] disasterCenter, String description) {
        if (ObjectUtil.isNull(startLng) || ObjectUtil.isNull(startLat) || ObjectUtil.isNull(endLng) || ObjectUtil.isNull(endLat)) {
            return null;
        }
        double distanceMeters = calculateDistanceMeters(startLng, startLat, endLng, endLat);
        if (distanceMeters < 40D) {
            return null;
        }
        List<EmergencyRoutePointResult> pointList = buildRoutePointList(startName, startLng, startLat, endName, endLng, endLat,
                disasterCenter, priorityLevel);
        if (pointList.size() < 2) {
            return null;
        }
        EmergencyRescueRouteResult result = new EmergencyRescueRouteResult();
        result.setRouteId(IdUtil.fastSimpleUUID());
        result.setRouteName(routeName);
        result.setRouteType(routeType);
        result.setDescription(description);
        result.setPriorityLevel(priorityLevel);
        result.setLineColor(lineColor);
        result.setEstimatedMinutes(estimateRouteMinutes(pointList, routeType));
        result.setPointList(pointList);
        return result;
    }

    private List<EmergencyRoutePointResult> buildRoutePointList(String startName, Double startLng, Double startLat,
                                                                String endName, Double endLng, Double endLat,
                                                                double[] disasterCenter, int priorityLevel) {
        List<EmergencyRoutePointResult> result = new ArrayList<>();
        result.add(buildRoutePoint(startName, "start", startLng, startLat));
        EmergencyRoutePointResult viaPoint = buildRouteViaPoint(startLng, startLat, endLng, endLat, disasterCenter, priorityLevel);
        if (ObjectUtil.isNotEmpty(viaPoint)) {
            result.add(viaPoint);
        }
        result.add(buildRoutePoint(endName, "end", endLng, endLat));
        return result;
    }

    private EmergencyRoutePointResult buildRoutePoint(String pointName, String pointRole, Double lng, Double lat) {
        EmergencyRoutePointResult result = new EmergencyRoutePointResult();
        result.setPointName(pointName);
        result.setPointRole(pointRole);
        result.setLng(round(ObjectUtil.defaultIfNull(lng, DEFAULT_CENTER_LNG), 6));
        result.setLat(round(ObjectUtil.defaultIfNull(lat, DEFAULT_CENTER_LAT), 6));
        return result;
    }

    private EmergencyRoutePointResult buildRouteViaPoint(Double startLng, Double startLat, Double endLng, Double endLat,
                                                         double[] disasterCenter, int priorityLevel) {
        double eastNorthDistance = calculateDistanceMeters(startLng, startLat, endLng, endLat);
        if (eastNorthDistance < 180D) {
            return null;
        }
        double baseLng = (startLng + endLng + disasterCenter[0]) / 3D;
        double baseLat = (startLat + endLat + disasterCenter[1]) / 3D;
        Map<String, Double> deltaMap = calculateLineDeltaMap(startLng, startLat, endLng, endLat);
        double normalLength = Math.sqrt(deltaMap.get("east") * deltaMap.get("east") + deltaMap.get("north") * deltaMap.get("north"));
        if (normalLength < 1D) {
            return null;
        }
        double bendMeters = clampRouteBendMeters(eastNorthDistance);
        double direction = priorityLevel % 2 == 0 ? -1D : 1D;
        double viaEastMeters = (-deltaMap.get("north") / normalLength) * bendMeters * direction;
        double viaNorthMeters = (deltaMap.get("east") / normalLength) * bendMeters * direction;
        return buildRoutePoint("中继调度点", "via",
                offsetLng(baseLng, baseLat, viaEastMeters),
                offsetLat(baseLat, viaNorthMeters));
    }

    private double clampRouteBendMeters(double distanceMeters) {
        return Math.max(55D, Math.min(distanceMeters * 0.18D, 150D));
    }

    private Map<String, Double> calculateLineDeltaMap(Double startLng, Double startLat, Double endLng, Double endLat) {
        Map<String, Double> result = new LinkedHashMap<>();
        double avgLat = (ObjectUtil.defaultIfNull(startLat, DEFAULT_CENTER_LAT) + ObjectUtil.defaultIfNull(endLat, DEFAULT_CENTER_LAT)) / 2D;
        result.put("east", (ObjectUtil.defaultIfNull(endLng, DEFAULT_CENTER_LNG) - ObjectUtil.defaultIfNull(startLng, DEFAULT_CENTER_LNG))
                * 111320D * Math.max(Math.cos(Math.toRadians(avgLat)), 0.2D));
        result.put("north", (ObjectUtil.defaultIfNull(endLat, DEFAULT_CENTER_LAT) - ObjectUtil.defaultIfNull(startLat, DEFAULT_CENTER_LAT))
                * 110540D);
        return result;
    }

    private Integer estimateRouteMinutes(List<EmergencyRoutePointResult> pointList, String routeType) {
        double totalDistanceMeters = 0D;
        for (int index = 0; index < pointList.size() - 1; index++) {
            EmergencyRoutePointResult currentPoint = pointList.get(index);
            EmergencyRoutePointResult nextPoint = pointList.get(index + 1);
            totalDistanceMeters += calculateDistanceMeters(currentPoint.getLng(), currentPoint.getLat(), nextPoint.getLng(), nextPoint.getLat());
        }
        double speedMetersPerMinute = "medical".equals(routeType) ? 560D : "evacuation".equals(routeType) ? 420D : 460D;
        return Math.max(3, (int) Math.ceil(totalDistanceMeters / speedMetersPerMinute) + 2);
    }

    private String buildAiRouteSummary(List<EmergencyRescueRouteResult> routeList) {
        if (ObjectUtil.isEmpty(routeList)) {
            return "当前尚未形成明确的进场路线。";
        }
        String routeSummary = routeList.stream()
                .limit(3)
                .map(item -> StrUtil.format("{}({}->{})",
                        item.getRouteName(),
                        resolveRoutePointName(item, 0, "起点"),
                        resolveRoutePointName(item, -1, "终点")))
                .collect(Collectors.joining("、"));
        return StrUtil.format("已生成{}条救援路线：{}。", routeList.size(), routeSummary);
    }

    private String resolveRoutePointName(EmergencyRescueRouteResult emergencyRescueRouteResult, int index, String fallbackName) {
        if (ObjectUtil.isEmpty(emergencyRescueRouteResult) || ObjectUtil.isEmpty(emergencyRescueRouteResult.getPointList())) {
            return fallbackName;
        }
        int safeIndex = index >= 0 ? index : emergencyRescueRouteResult.getPointList().size() + index;
        if (safeIndex < 0 || safeIndex >= emergencyRescueRouteResult.getPointList().size()) {
            return fallbackName;
        }
        EmergencyRoutePointResult emergencyRoutePointResult = emergencyRescueRouteResult.getPointList().get(safeIndex);
        return StrUtil.blankToDefault(emergencyRoutePointResult.getPointName(), fallbackName);
    }

    private String resolveAiWarningStyleKey(String warningLabel) {
        if ("红色预警".equals(warningLabel)) {
            return "critical";
        }
        if ("橙色预警".equals(warningLabel)) {
            return "warning";
        }
        return "notice";
    }

    private String resolveAiWarningLabel(Double radiusMeters, int affectedCount, EmergencySignalResult emergencySignalResult) {
        if (ObjectUtil.isNotEmpty(emergencySignalResult) && "RISK_AGGRAVATED".equals(emergencySignalResult.getSignalType())) {
            return "红色预警";
        }
        if (NumberUtil.compare(ObjectUtil.defaultIfNull(radiusMeters, DEFAULT_RADIUS_METERS), 2200D) >= 0 || affectedCount >= 20) {
            return "红色预警";
        }
        if (NumberUtil.compare(ObjectUtil.defaultIfNull(radiusMeters, DEFAULT_RADIUS_METERS), 1700D) >= 0 || affectedCount >= 12) {
            return "橙色预警";
        }
        return "黄色预警";
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
