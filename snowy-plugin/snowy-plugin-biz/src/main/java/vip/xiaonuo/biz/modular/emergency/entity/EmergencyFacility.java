package vip.xiaonuo.biz.modular.emergency.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import vip.xiaonuo.common.pojo.CommonEntity;

/**
 * 应急设施实体
 *
 * @author Codex
 * @date 2026/3/20
 */
@Getter
@Setter
@TableName("BIZ_EMERGENCY_FACILITY")
public class EmergencyFacility extends CommonEntity {

    /** 主键 */
    @ApiModelProperty(value = "主键", position = 1)
    private String id;

    /** 租户id */
    @ApiModelProperty(value = "租户id", position = 2)
    private String tenantId;

    /** 设施名称 */
    @ApiModelProperty(value = "设施名称", position = 3)
    private String name;

    /** 设施类型 */
    @ApiModelProperty(value = "设施类型", position = 4)
    private String type;

    /** 经度 */
    @ApiModelProperty(value = "经度", position = 5)
    private Double lng;

    /** 纬度 */
    @ApiModelProperty(value = "纬度", position = 6)
    private Double lat;

    /** 排序码 */
    @ApiModelProperty(value = "排序码", position = 7)
    private Integer sortCode;
}
