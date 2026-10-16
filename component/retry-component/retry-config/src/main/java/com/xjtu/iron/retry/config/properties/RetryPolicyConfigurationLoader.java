package com.xjtu.iron.retry.config.properties;

import com.xjtu.iron.retry.api.policy.RetryPolicy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 将 Spring 外部配置解析为可直接注册的不可变重试策略。
 *
 * <p>自动配置层只依赖该门面，继承解析和异常类型加载等细节保持在 properties 包内部。</p>
 */
public final class RetryPolicyConfigurationLoader {

    private final RetryPolicyPropertiesResolver propertiesResolver;
    private final RetryPolicyFactory policyFactory;

    public RetryPolicyConfigurationLoader() {
        this(new RetryPolicyPropertiesResolver(), new RetryPolicyFactory());
    }

    RetryPolicyConfigurationLoader(
            RetryPolicyPropertiesResolver propertiesResolver,
            RetryPolicyFactory policyFactory) {
        this.propertiesResolver = Objects.requireNonNull(
                propertiesResolver,
                "propertiesResolver must not be null"
        );
        this.policyFactory = Objects.requireNonNull(
                policyFactory,
                "policyFactory must not be null"
        );
    }

    /** 解析全部命名策略，并保持配置声明顺序返回不可变快照。 */
    public Map<String, RetryPolicy> load(RetryProperties properties) {
        Map<String, ResolvedRetryPolicyProperties> resolvedPolicies =
                propertiesResolver.resolve(properties);
        Map<String, RetryPolicy> policies = new LinkedHashMap<>();
        for (Map.Entry<String, ResolvedRetryPolicyProperties> entry
                : resolvedPolicies.entrySet()) {
            policies.put(entry.getKey(), policyFactory.create(entry.getKey(), entry.getValue()));
        }
        return Collections.unmodifiableMap(policies);
    }
}
