package com.xjtu.iron.cache.demo;

/**
 * 营销活动规则 DTO。
 *
 * <p>用于 Demo 演示缓存 value 的序列化和反序列化。</p>
 */
public class CampaignRuleDTO {

    /** 活动 ID。 */
    private Long campaignId;

    /** 规则名称。 */
    private String ruleName;

    /** 规则内容。 */
    private String ruleContent;

    public Long getCampaignId() { return campaignId; }
    public void setCampaignId(Long campaignId) { this.campaignId = campaignId; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public String getRuleContent() { return ruleContent; }
    public void setRuleContent(String ruleContent) { this.ruleContent = ruleContent; }
}
