package vip.xiaonuo.biz.modular.emergency.result;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * POI范围查询结果
 *
 * @author Codex
 * @date 2026/3/19
 */
@Getter
@Setter
public class EmergencyPoiQueryResult {

    /** 查询中心经度 */
    @ApiModelProperty(value = "查询中心经度")
    private Double centerLng;

    /** 查询中心纬度 */
    @ApiModelProperty(value = "查询中心纬度")
    private Double centerLat;

    /** 查询半径 */
    @ApiModelProperty(value = "查询半径，单位米")
    private Double radiusMeters;

    /** 总POI数 */
    @ApiModelProperty(value = "模拟POI总数")
    private Integer totalPoiCount;

    /** 命中数量 */
    @ApiModelProperty(value = "范围内POI数量")
    private Integer matchedCount;

    /** 命中POI列表 */
    @ApiModelProperty(value = "范围内POI列表")
    private List<EmergencyPoiResult> poiList;
}
