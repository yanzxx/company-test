package vip.xiaonuo.biz.modular.emergency.result;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;

/**
 * AI救援方案建议结果
 *
 * @author Codex
 * @date 2026/3/20
 */
@Getter
@Setter
public class EmergencyAiSuggestionResult {

    /** 建议来源 */
    @ApiModelProperty(value = "建议来源")
    private String providerName;

    /** 生成模式 */
    @ApiModelProperty(value = "生成模式")
    private String modeLabel;

    /** 风险概述 */
    @ApiModelProperty(value = "风险概述")
    private String riskSummary;

    /** 建议正文 */
    @ApiModelProperty(value = "建议正文")
    private String suggestion;

    /** 行动要点 */
    @ApiModelProperty(value = "行动要点")
    private List<String> actionList;

    /** 救援路线 */
    @ApiModelProperty(value = "救援路线")
    private List<EmergencyRescueRouteResult> routeList;

    /** Prompt标题 */
    @ApiModelProperty(value = "Prompt标题")
    private String promptTitle;

    /** Prompt模板 */
    @ApiModelProperty(value = "Prompt模板")
    private String promptTemplate;

    /** GeoJSON示例 */
    @ApiModelProperty(value = "GeoJSON示例")
    private String geoJsonExample;

    /** 空间计算代码示例 */
    @ApiModelProperty(value = "空间计算代码示例")
    private String spatialCodeExample;

    /** 生成时间 */
    @ApiModelProperty(value = "生成时间")
    private Date generatedTime;
}
