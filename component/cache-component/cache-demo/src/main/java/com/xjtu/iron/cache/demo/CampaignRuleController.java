package com.xjtu.iron.cache.demo;

import com.xjtu.iron.cache.api.model.CacheResult;
import org.springframework.web.bind.annotation.*;

/**
 * 缓存组件调试接口。
 */
@RestController
@RequestMapping("/demo/campaign-rules")
public class CampaignRuleController {

    /** 活动规则查询服务。 */
    private final CampaignRuleQueryService campaignRuleQueryService;

    /** 创建 Controller。 */
    public CampaignRuleController(CampaignRuleQueryService campaignRuleQueryService) {
        this.campaignRuleQueryService = campaignRuleQueryService;
    }

    /** 普通查询接口，只返回 DTO。 */
    @GetMapping("/{campaignId}")
    public CampaignRuleDTO query(@PathVariable("campaignId")  Long campaignId) {
        return campaignRuleQueryService.queryRule(campaignId);
    }

    /** 调试查询接口，返回命中层级、状态、耗时等信息。 */
    @GetMapping("/{campaignId}/debug")
    public CacheResult<CampaignRuleDTO> queryDebug(@PathVariable("campaignId") Long campaignId) {
        return campaignRuleQueryService.queryRuleResult(campaignId);
    }

    /** 删除指定活动规则缓存。 */
    @DeleteMapping("/{campaignId}/cache")
    public String evict(@PathVariable("campaignId")  Long campaignId) {
        campaignRuleQueryService.evictRule(campaignId);
        return "OK";
    }

    /** 查看源数据加载次数，用于验证缓存是否生效。 */
    @GetMapping("/source-load-count")
    public int sourceLoadCount() {
        return campaignRuleQueryService.getSourceLoadCount();
    }
}
