package vip.xiaonuo.biz.modular.emergency.param;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * 演练日志查询参数
 *
 * @author Codex
 * @date 2026/3/19
 */
@Getter
@Setter
public class EmergencyLogQueryParam {

    /** 返回条数 */
    @ApiModelProperty(value = "返回条数，默认50，最大200")
    @Min(value = 1, message = "返回条数最小为1")
    @Max(value = 200, message = "返回条数最大为200")
    private Integer limit;
}
