package com.xjtu.iron.retry.core.policy;

import com.xjtu.iron.foundation.core.validation.Arguments;
import com.xjtu.iron.retry.api.policy.RetryPolicy;
import com.xjtu.iron.retry.api.policy.RetryPolicyRegistry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** 使用并发内存映射表管理命名重试策略。 */
public final class DefaultRetryPolicyRegistry implements RetryPolicyRegistry {

    /** 保存当前可见的不可变策略快照。 */
    private final Map<String, RetryPolicy> policies = new ConcurrentHashMap<>();

    /** 注册新策略并拒绝静默覆盖同名策略。 */
    @Override
    public void register(RetryPolicy retryPolicy) {
        RetryPolicy actualPolicy = requirePolicy(retryPolicy);
        RetryPolicy previous = policies.putIfAbsent(
                actualPolicy.getPolicyName(),
                actualPolicy
        );
        if (previous != null) {
            throw new IllegalArgumentException(
                    "Retry policy already exists: " + actualPolicy.getPolicyName()
            );
        }
    }

    /** 显式替换同名策略或在不存在时新增。 */
    @Override
    public void replace(RetryPolicy retryPolicy) {
        RetryPolicy actualPolicy = requirePolicy(retryPolicy);
        policies.put(actualPolicy.getPolicyName(), actualPolicy);
    }

    /** 查找可选命名策略。 */
    @Override
    public Optional<RetryPolicy> find(String policyName) {
        return Optional.ofNullable(policies.get(requireText(policyName)));
    }

    @Override
    public RetryPolicy getRequired(String policyName) {
        String actualName = requireText(policyName);
        RetryPolicy retryPolicy = policies.get(actualName);
        if (retryPolicy == null) {
            throw new IllegalArgumentException("Retry policy does not exist: " + actualName);
        }
        return retryPolicy;
    }

    /** 返回按名称排序的不可变策略名集合。 */
    @Override
    public Collection<String> policyNames() {
        ArrayList<String> names = new ArrayList<>(policies.keySet());
        Collections.sort(names);
        return Collections.unmodifiableList(names);
    }

    /** 返回按名称排序的不可变策略映射快照。 */
    @Override
    public Map<String, RetryPolicy> snapshot() {
        return Collections.unmodifiableMap(new TreeMap<>(policies));
    }

    /** 校验策略非空。 */
    private static RetryPolicy requirePolicy(RetryPolicy retryPolicy) {
        return Arguments.notNull(retryPolicy, "retryPolicy");
    }

    /** 校验策略名称非空且非空白。 */
    private static String requireText(String value) {
        return Arguments.notBlank(value, "policyName").trim();
    }
}
