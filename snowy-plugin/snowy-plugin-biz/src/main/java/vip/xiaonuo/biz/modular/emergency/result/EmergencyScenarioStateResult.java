package vip.xiaonuo.biz.modular.emergency.result;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 当前灾情态势结果
 *
 * @author Codex
 * @date 2026/3/19
 */
@Getter
@Setter
public class EmergencyScenarioStateResult {

    /** 中心经度 */
    @ApiModelProperty(value = "当前灾情中心经度")
    private Double centerLng;

    /** 中心纬度 */
    @ApiModelProperty(value = "当前灾情中心纬度")
    private Double centerLat;

    /** 当前半径 */
    @ApiModelProperty(value = "当前灾情半径，单位米")
    private Double radiusMeters;

    /** 总设施数 */
    @ApiModelProperty(value = "应急设施总数")
    private Integer totalPoiCount;

    /** 当前影响设施数量 */
    @ApiModelProperty(value = "当前影响设施数量")
    private Integer poiCountInRange;

    /** 当前影响设施列表 */
    @ApiModelProperty(value = "当前影响设施列表")
    private List<EmergencyPoiResult> affectedPoiList;

    /** 总设施类型统计 */
    @ApiModelProperty(value = "总设施类型统计")
    private List<EmergencyFacilityStatResult> totalFacilityStats;

    /** 当前受灾设施类型统计 */
    @ApiModelProperty(value = "当前受灾设施类型统计")
    private List<EmergencyFacilityStatResult> affectedFacilityStats;

    /** 累计信号数 */
    @ApiModelProperty(value = "累计推送信号数")
    private Long signalCounter;

    /** 最近信号 */
    @ApiModelProperty(value = "最近一次灾情信号")
    private EmergencySignalResult latestSignal;

    /** 最近漏水点 */
    @ApiModelProperty(value = "最近漏水点列表")
    private List<EmergencyLeakPointResult> leakPointList;
}
