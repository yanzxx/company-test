package vip.xiaonuo.biz.modular.emergency.param;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 演练操作日志记录参数
 *
 * @author Codex
 * @date 2026/3/20
 */
@Getter
@Setter
public class EmergencyLogRecordParam {

    /** 操作类型 */
    @ApiModelProperty(value = "操作类型", required = true)
    @NotBlank(message = "操作类型不能为空")
    @Size(max = 64, message = "操作类型长度不能超过64个字符")
    private String operationType;

    /** 操作名称 */
    @ApiModelProperty(value = "操作名称", required = true)
    @NotBlank(message = "操作名称不能为空")
    @Size(max = 128, message = "操作名称长度不能超过128个字符")
    private String operationName;

    /** 详细描述 */
    @ApiModelProperty(value = "操作详情")
    @Size(max = 500, message = "操作详情长度不能超过500个字符")
    private String detail;
}
