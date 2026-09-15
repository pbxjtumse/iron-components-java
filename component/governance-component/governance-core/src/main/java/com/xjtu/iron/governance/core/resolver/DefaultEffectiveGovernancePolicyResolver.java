package com.xjtu.iron.governance.core.resolver;

import com.xjtu.iron.governance.api.context.GovernanceContext;
import com.xjtu.iron.governance.config.api.GovernanceRuleRepository;
import com.xjtu.iron.governance.model.policy.GovernancePolicy;
import com.xjtu.iron.governance.model.rule.GovernanceRuleSet;

/**
 * 这里一期为了简单直接返回对象。后面二期要做深拷贝和策略合并，否则动态配置时可能互相污染。
 */
public class DefaultEffectiveGovernancePolicyResolver implements EffectiveGovernancePolicyResolver {

    private final GovernanceRuleRepository ruleRepository;

    public DefaultEffectiveGovernancePolicyResolver(GovernanceRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    @Override
    public GovernancePolicy resolve(GovernanceContext context) {
        GovernanceRuleSet ruleSet = ruleRepository.current();

        GovernancePolicy resourcePolicy = ruleSet.getResources().get(context.getResourceName());

        if (resourcePolicy != null) {
            resourcePolicy.setResourceName(context.getResourceName());
            return resourcePolicy;
        }

        GovernancePolicy defaultPolicy = ruleSet.getDefaultPolicy();
        defaultPolicy.setResourceName(context.getResourceName());
        return defaultPolicy;
    }


}
