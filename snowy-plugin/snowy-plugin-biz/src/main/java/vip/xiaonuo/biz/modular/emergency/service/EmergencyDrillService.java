package vip.xiaonuo.biz.modular.emergency.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vip.xiaonuo.biz.modular.emergency.param.EmergencyLogQueryParam;
import vip.xiaonuo.biz.modular.emergency.param.EmergencyLogRecordParam;
import vip.xiaonuo.biz.modular.emergency.param.EmergencyPoiQueryParam;
import vip.xiaonuo.biz.modular.emergency.param.EmergencyRadiusUpdateParam;
import vip.xiaonuo.biz.modular.emergency.result.EmergencyAiSuggestionResult;
import vip.xiaonuo.biz.modular.emergency.result.EmergencyDrillLogResult;
import vip.xiaonuo.biz.modular.emergency.result.EmergencyPoiQueryResult;
import vip.xiaonuo.biz.modular.emergency.result.EmergencyPoiResult;
import vip.xiaonuo.biz.modular.emergency.result.EmergencyScenarioStateResult;

import java.util.List;

/**
 * 应急演练大屏Service接口
 *
 * @author Codex
 * @date 2026/3/19
 */
public interface EmergencyDrillService {

    /**
     * 获取模拟POI数据
     */
    List<EmergencyPoiResult> mockPoiList();

    /**
     * 查询圆形范围内POI
     */
    EmergencyPoiQueryResult queryInRange(EmergencyPoiQueryParam emergencyPoiQueryParam);

    /**
     * 获取当前灾情态势
     */
    EmergencyScenarioStateResult disasterState();

    /**
     * 获取AI救援方案建议
     */
    EmergencyAiSuggestionResult aiSuggestion();

    /**
     * 更新半径
     */
    EmergencyScenarioStateResult updateRadius(EmergencyRadiusUpdateParam emergencyRadiusUpdateParam);

    /**
     * 订阅推送
     */
    SseEmitter disasterStream();

    /**
     * 获取演练日志
     */
    List<EmergencyDrillLogResult> logList(EmergencyLogQueryParam emergencyLogQueryParam);

    /**
     * 记录演练操作日志
     */
    void recordLog(EmergencyLogRecordParam emergencyLogRecordParam);
}
