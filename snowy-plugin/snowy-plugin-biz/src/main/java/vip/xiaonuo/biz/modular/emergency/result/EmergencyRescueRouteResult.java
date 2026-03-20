package vip.xiaonuo.biz.modular.emergency.result;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * AI救援路线结果
 *
 * @author Codex
 * @date 2026/3/20
 */
@Getter
@Setter
public class EmergencyRescueRouteResult {

    /** 路线ID */
    @ApiModelProperty(value = "路线ID")
    private String routeId;

    /** 路线名称 */
    @ApiModelProperty(value = "路线名称")
    private String routeName;

    /** 路线类型 */
    @ApiModelProperty(value = "路线类型")
    private String routeType;

    /** 路线说明 */
    @ApiModelProperty(value = "路线说明")
    private String description;

    /** 优先级 */
    @ApiModelProperty(value = "优先级")
    private Integer priorityLevel;

    /** 线颜色 */
    @ApiModelProperty(value = "线颜色")
    private String lineColor;

    /** 预计到达分钟数 */
    @ApiModelProperty(value = "预计到达分钟数")
    private Integer estimatedMinutes;

    /** 路线节点 */
    @ApiModelProperty(value = "路线节点")
    private List<EmergencyRoutePointResult> pointList;
}
