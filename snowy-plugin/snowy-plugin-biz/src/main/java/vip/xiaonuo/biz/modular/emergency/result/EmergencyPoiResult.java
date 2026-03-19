package vip.xiaonuo.biz.modular.emergency.result;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 模拟POI结果
 *
 * @author Codex
 * @date 2026/3/19
 */
@Getter
@Setter
public class EmergencyPoiResult {

    /** 主键 */
    @ApiModelProperty(value = "POI主键")
    private String id;

    /** 名称 */
    @ApiModelProperty(value = "POI名称")
    private String name;

    /** 类型 */
    @ApiModelProperty(value = "POI类型")
    private String type;

    /** 经度 */
    @ApiModelProperty(value = "经度")
    private Double lng;

    /** 纬度 */
    @ApiModelProperty(value = "纬度")
    private Double lat;

    /** 距离中心点距离 */
    @ApiModelProperty(value = "距离中心点距离，单位米")
    private Double distanceMeters;
}
