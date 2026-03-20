package vip.xiaonuo.biz.modular.emergency.controller;

import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
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
import vip.xiaonuo.biz.modular.emergency.service.EmergencyDrillService;
import vip.xiaonuo.common.annotation.CommonLog;
import vip.xiaonuo.common.pojo.CommonResult;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

/**
 * 应急演练大屏控制器
 *
 * @author Codex
 * @date 2026/3/19
 */
@Api(tags = "应急演练大屏控制器")
@ApiSupport(author = "CODEX", order = 10)
@RestController
@Validated
public class EmergencyDrillController {

    @Resource
    private EmergencyDrillService emergencyDrillService;

    /**
     * 获取模拟POI数据
     */
    @ApiOperationSupport(order = 1)
    @ApiOperation("获取模拟POI数据")
    @GetMapping("/biz/emergency/drill/poi/mockData")
    public CommonResult<List<EmergencyPoiResult>> mockPoiList() {
        return CommonResult.data(emergencyDrillService.mockPoiList());
    }

    /**
     * 查询受灾圆形范围内的POI
     */
    @ApiOperationSupport(order = 2)
    @ApiOperation("查询受灾圆形范围内的POI")
    @PostMapping("/biz/emergency/drill/poi/queryInRange")
    public CommonResult<EmergencyPoiQueryResult> queryInRange(@RequestBody @Valid EmergencyPoiQueryParam emergencyPoiQueryParam) {
        return CommonResult.data(emergencyDrillService.queryInRange(emergencyPoiQueryParam));
    }

    /**
     * 获取当前灾情态势
     */
    @ApiOperationSupport(order = 3)
    @ApiOperation("获取当前灾情态势")
    @GetMapping("/biz/emergency/drill/disaster/state")
    public CommonResult<EmergencyScenarioStateResult> disasterState() {
        return CommonResult.data(emergencyDrillService.disasterState());
    }

    /**
     * 获取AI救援方案建议
     */
    @ApiOperationSupport(order = 4)
    @ApiOperation("获取AI救援方案建议")
    @GetMapping("/biz/emergency/drill/ai/suggestion")
    public CommonResult<EmergencyAiSuggestionResult> aiSuggestion() {
        return CommonResult.data(emergencyDrillService.aiSuggestion());
    }

    /**
     * 更新受灾区域半径
     */
    @ApiOperationSupport(order = 5)
    @ApiOperation("更新受灾区域半径")
    @CommonLog("更新受灾区域半径")
    @PostMapping("/biz/emergency/drill/disaster/updateRadius")
    public CommonResult<EmergencyScenarioStateResult> updateRadius(@RequestBody @Valid EmergencyRadiusUpdateParam emergencyRadiusUpdateParam) {
        return CommonResult.data(emergencyDrillService.updateRadius(emergencyRadiusUpdateParam));
    }

    /**
     * 订阅灾情状态推送
     */
    @ApiOperationSupport(order = 6)
    @ApiOperation("订阅灾情状态推送")
    @GetMapping(value = "/biz/emergency/drill/disaster/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter disasterStream() {
        return emergencyDrillService.disasterStream();
    }

    /**
     * 获取演练操作日志
     */
    @ApiOperationSupport(order = 7)
    @ApiOperation("获取演练操作日志")
    @GetMapping("/biz/emergency/drill/log/list")
    public CommonResult<List<EmergencyDrillLogResult>> logList(@Valid EmergencyLogQueryParam emergencyLogQueryParam) {
        return CommonResult.data(emergencyDrillService.logList(emergencyLogQueryParam));
    }

    /**
     * 记录演练操作日志
     */
    @ApiOperationSupport(order = 8)
    @ApiOperation("记录演练操作日志")
    @PostMapping("/biz/emergency/drill/log/record")
    public CommonResult<Void> recordLog(@RequestBody @Valid EmergencyLogRecordParam emergencyLogRecordParam) {
        emergencyDrillService.recordLog(emergencyLogRecordParam);
        return CommonResult.ok();
    }
}
