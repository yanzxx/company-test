package vip.xiaonuo.biz.modular.emergency.result;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 应急设施统计结果
 *
 * @author Codex
 * @date 2026/3/20
 */
@Getter
@Setter
public class EmergencyFacilityStatResult {

    /** 设施类型 */
    @ApiModelProperty(value = "设施类型")
    private String type;

    /** 设施数量 */
    @ApiModelProperty(value = "设施数量")
    private Integer count;
}
