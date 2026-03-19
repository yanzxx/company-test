package vip.xiaonuo.biz.modular.emergency.result;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 漏水点结果
 *
 * @author Codex
 * @date 2026/3/19
 */
@Getter
@Setter
public class EmergencyLeakPointResult {

    /** 主键 */
    @ApiModelProperty(value = "漏水点主键")
    private String id;

    /** 名称 */
    @ApiModelProperty(value = "漏水点名称")
    private String name;

    /** 经度 */
    @ApiModelProperty(value = "经度")
    private Double lng;

    /** 纬度 */
    @ApiModelProperty(value = "纬度")
    private Double lat;

    /** 发现时间 */
    @ApiModelProperty(value = "发现时间")
    private Date detectedTime;
}
