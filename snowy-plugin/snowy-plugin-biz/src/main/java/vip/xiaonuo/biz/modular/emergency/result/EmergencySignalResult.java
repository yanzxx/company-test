package vip.xiaonuo.biz.modular.emergency.result;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;

/**
 * 灾情信号结果
 *
 * @author Codex
 * @date 2026/3/19
 */
@Getter
@Setter
public class EmergencySignalResult {

    /** 信号主键 */
    @ApiModelProperty(value = "信号主键")
    private String signalId;

    /** 信号流水号 */
    @ApiModelProperty(value = "信号流水号")
    private Long signalNo;

    /** 信号类型 */
    @ApiModelProperty(value = "信号类型")
    private String signalType;

    /** 信号名称 */
    @ApiModelProperty(value = "信号名称")
    private String signalName;

    /** 信号说明 */
    @ApiModelProperty(value = "信号说明")
    private String message;

    /** 信号时间 */
    @ApiModelProperty(value = "信号时间")
    private Date signalTime;

    /** 中心经度 */
    @ApiModelProperty(value = "当前中心经度")
    private Double centerLng;

    /** 中心纬度 */
    @ApiModelProperty(value = "当前中心纬度")
    private Double centerLat;

    /** 当前半径 */
    @ApiModelProperty(value = "当前半径，单位米")
    private Double radiusMeters;

    /** 半径变化值 */
    @ApiModelProperty(value = "半径变化值，单位米")
    private Double radiusDeltaMeters;

    /** 受影响POI数 */
    @ApiModelProperty(value = "受影响POI数")
    private Integer affectedPoiCount;

    /** 当前受影响POI列表 */
    @ApiModelProperty(value = "当前受影响POI列表")
    private List<EmergencyPoiResult> affectedPoiList;

    /** 漏水点 */
    @ApiModelProperty(value = "漏水点信息")
    private EmergencyLeakPointResult leakPoint;
}
