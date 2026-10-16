package com.xjtu.iron.retry.config.properties;

import java.util.*;

/** 解析命名策略继承、默认值覆盖、显式列表清空和循环依赖。 */
final class RetryPolicyPropertiesResolver {

    /** 解析全部命名策略并保持配置声明顺序。 */
    Map<String, ResolvedRetryPolicyProperties> resolve(RetryProperties properties) {
        RetryProperties actualProperties = Objects.requireNonNull(
                properties,
                "properties must not be null"
        );
        Map<String, ResolvedRetryPolicyProperties> resolved = new LinkedHashMap<>();
        for (String policyName : actualProperties.getPolicies().keySet()) {
            resolveOne(policyName, actualProperties, resolved, new ArrayDeque<>());
        }
        return resolved;
    }

    /** 使用深度优先方式解析一个策略及其父策略。 */
    private ResolvedRetryPolicyProperties resolveOne(
            String policyName,
            RetryProperties properties,
            Map<String, ResolvedRetryPolicyProperties> resolved,
            Deque<String> inheritancePath) {
        String actualPolicyName = requireText(policyName, "policyName");
        ResolvedRetryPolicyProperties cached = resolved.get(actualPolicyName);
        if (cached != null) {
            return cached;
        }
        RetryProperties.PolicyProperties source = properties
                .getPolicies()
                .get(actualPolicyName);
        if (source == null) {
            throw new IllegalArgumentException(
                    "Retry policy does not exist: " + actualPolicyName
            );
        }
        if (inheritancePath.contains(actualPolicyName)) {
            ArrayList<String> cycle = new ArrayList<>(inheritancePath);
            cycle.add(actualPolicyName);
            throw new IllegalArgumentException(
                    "Circular retry policy inheritance detected: " + cycle
            );
        }
        inheritancePath.addLast(actualPolicyName);
        try {
            ResolvedRetryPolicyProperties target = createInheritedTarget(
                    source,
                    properties,
                    resolved,
                    inheritancePath
            );
            merge(target, source);
            resolved.put(actualPolicyName, target);
            return target;
        } finally {
            inheritancePath.removeLast();
        }
    }

    /** 创建默认配置或父策略深复制。 */
    private ResolvedRetryPolicyProperties createInheritedTarget(
            RetryProperties.PolicyProperties source,
            RetryProperties properties,
            Map<String, ResolvedRetryPolicyProperties> resolved,
            Deque<String> inheritancePath) {
        String basePolicy = source.getBasePolicy();
        if (basePolicy == null || basePolicy.isBlank()) {
            return new ResolvedRetryPolicyProperties();
        }
        return resolveOne(basePolicy, properties, resolved, inheritancePath).copy();
    }

    /** 将子策略显式字段覆盖到继承结果。 */
    private void merge(
            ResolvedRetryPolicyProperties target,
            RetryProperties.PolicyProperties source) {
        if (source.getMaxAttempts() != null) {
            target.maxAttempts = source.getMaxAttempts();
        }
        if (source.getMaxDuration() != null) {
            target.maxDuration = source.getMaxDuration();
        }
        if (source.getOperationSafety() != null) {
            target.operationSafety = source.getOperationSafety();
        }
        if (source.getSafetyMode() != null) {
            target.safetyMode = source.getSafetyMode();
        }
        if (source.getTraverseCauses() != null) {
            target.traverseCauses = source.getTraverseCauses();
        }
        if (source.getMaxCauseDepth() != null) {
            target.maxCauseDepth = source.getMaxCauseDepth();
        }
        if (source.getRetryFailureCategory() != null) {
            target.retryFailureCategory = source.getRetryFailureCategory();
        }
        if (source.getRetryFailureCode() != null) {
            target.retryFailureCode = requireText(
                    source.getRetryFailureCode(),
                    "retryFailureCode"
            );
        }
        if (source.getRetryOn() != null) {
            target.retryOn = new ArrayList<>(source.getRetryOn());
        }
        if (source.getStopOn() != null) {
            target.stopOn = new ArrayList<>(source.getStopOn());
        }
        if (source.getAbortOn() != null) {
            target.abortOn = new ArrayList<>(source.getAbortOn());
        }
        mergeBackoff(target, source.getBackoff());
    }

    /** 将可选退避字段覆盖到继承结果。 */
    private void mergeBackoff(
            ResolvedRetryPolicyProperties target,
            RetryProperties.BackoffProperties backoff) {
        if (backoff == null) {
            return;
        }
        if (backoff.getType() != null) {
            target.backoffType = backoff.getType();
        }
        if (backoff.getDelay() != null) {
            target.delay = backoff.getDelay();
        }
        if (backoff.getInitialDelay() != null) {
            target.initialDelay = backoff.getInitialDelay();
        }
        if (backoff.getMaxDelay() != null) {
            target.maxDelay = backoff.getMaxDelay();
        }
        if (backoff.getMultiplier() != null) {
            target.multiplier = backoff.getMultiplier();
        }
    }

    /** 校验文本非空且非空白。 */
    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
