package vip.xiaonuo.biz.modular.emergency.result;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 演练操作日志结果
 *
 * @author Codex
 * @date 2026/3/19
 */
@Getter
@Setter
public class EmergencyDrillLogResult {

    /** 主键 */
    @ApiModelProperty(value = "日志主键")
    private String id;

    /** 操作类型 */
    @ApiModelProperty(value = "操作类型")
    private String operationType;

    /** 操作名称 */
    @ApiModelProperty(value = "操作名称")
    private String operationName;

    /** 操作人id */
    @ApiModelProperty(value = "操作人id")
    private String operatorId;

    /** 操作人名称 */
    @ApiModelProperty(value = "操作人名称")
    private String operatorName;

    /** 日志描述 */
    @ApiModelProperty(value = "日志描述")
    private String detail;

    /** 记录时间 */
    @ApiModelProperty(value = "记录时间")
    private Date createTime;
}
