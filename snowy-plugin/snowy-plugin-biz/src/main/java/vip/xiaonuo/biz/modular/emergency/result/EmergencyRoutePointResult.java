package vip.xiaonuo.biz.modular.emergency.result;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 救援路线节点结果
 *
 * @author Codex
 * @date 2026/3/20
 */
@Getter
@Setter
public class EmergencyRoutePointResult {

    /** 节点名称 */
    @ApiModelProperty(value = "节点名称")
    private String pointName;

    /** 节点角色 */
    @ApiModelProperty(value = "节点角色")
    private String pointRole;

    /** 经度 */
    @ApiModelProperty(value = "经度")
    private Double lng;

    /** 纬度 */
    @ApiModelProperty(value = "纬度")
    private Double lat;
}
