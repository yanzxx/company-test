package vip.xiaonuo.biz.modular.emergency.param;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;

/**
 * 圆形范围POI查询参数
 *
 * @author Codex
 * @date 2026/3/19
 */
@Getter
@Setter
public class EmergencyPoiQueryParam {

    /** 中心经度 */
    @ApiModelProperty(value = "中心点经度", required = true)
    @NotNull(message = "中心点经度不能为空")
    @DecimalMin(value = "-180.0", message = "中心点经度不能小于-180")
    @DecimalMax(value = "180.0", message = "中心点经度不能大于180")
    private Double centerLng;

    /** 中心纬度 */
    @ApiModelProperty(value = "中心点纬度", required = true)
    @NotNull(message = "中心点纬度不能为空")
    @DecimalMin(value = "-90.0", message = "中心点纬度不能小于-90")
    @DecimalMax(value = "90.0", message = "中心点纬度不能大于90")
    private Double centerLat;

    /** 半径 */
    @ApiModelProperty(value = "受灾圆半径，单位米", required = true)
    @NotNull(message = "受灾半径不能为空")
    @DecimalMin(value = "1.0", message = "受灾半径必须大于0")
    private Double radiusMeters;
}
