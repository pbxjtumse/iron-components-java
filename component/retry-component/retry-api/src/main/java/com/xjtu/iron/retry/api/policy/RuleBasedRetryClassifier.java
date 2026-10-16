package com.xjtu.iron.retry.api.policy;

import com.xjtu.iron.retry.api.execution.RetryAttempt;

import java.util.*;

/** 根据 RetryPolicy.Builder 中声明的异常和结果规则执行保守分类。 */
final class RuleBasedRetryClassifier implements RetryClassifier {

    private final List<RetryExceptionRule> retryRules;
    private final List<RetryExceptionRule> stopRules;
    private final List<RetryExceptionRule> abortRules;
    private final List<RetryResultRule> resultRules;
    private final boolean traverseCauses;
    private final int maxCauseDepth;

    RuleBasedRetryClassifier(
            List<RetryExceptionRule> retryRules,
            List<RetryExceptionRule> stopRules,
            List<RetryExceptionRule> abortRules,
            List<RetryResultRule> resultRules,
            boolean traverseCauses,
            int maxCauseDepth) {
        this.retryRules = immutableCopy(retryRules);
        this.stopRules = immutableCopy(stopRules);
        this.abortRules = immutableCopy(abortRules);
        this.resultRules = Collections.unmodifiableList(new ArrayList<>(resultRules));
        this.traverseCauses = traverseCauses;
        this.maxCauseDepth = maxCauseDepth;
    }

    /** 按 ABORT、STOP、RETRY 的安全优先级解释一次物理尝试。 */
    @Override
    public RetryDecision classify(RetryAttempt<?> attempt) {
        RetryAttempt<?> actualAttempt = Objects.requireNonNull(
                attempt,
                "attempt must not be null"
        );
        Throwable failure = actualAttempt.getFailure();
        if (failure == null) {
            return classifyResult(actualAttempt.getResult());
        }
        if (failure instanceof InterruptedException) {
            return RetryDecision.abort("operation was interrupted", "INTERRUPTED");
        }
        RetryExceptionRule abortRule = findBestRule(abortRules, failure);
        if (abortRule != null) {
            return RetryDecision.abort(
                    "failure matched abort rule",
                    abortRule.failureCode()
            );
        }
        RetryExceptionRule stopRule = findBestRule(stopRules, failure);
        if (stopRule != null) {
            return RetryDecision.stop(
                    "failure matched stop rule",
                    stopRule.failureCode(),
                    stopRule.category()
            );
        }
        RetryExceptionRule retryRule = findBestRule(retryRules, failure);
        if (retryRule != null) {
            return RetryDecision.retry(
                    "failure matched retry rule",
                    retryRule.failureCode(),
                    retryRule.category()
            );
        }
        return RetryDecision.stop(
                "failure did not match an explicit retry rule",
                "UNCLASSIFIED_FAILURE",
                RetryFailureCategory.NON_RETRYABLE
        );
    }

    private RetryDecision classifyResult(Object result) {
        for (RetryResultRule rule : resultRules) {
            if (rule.predicate().test(result)) {
                return RetryDecision.retry(
                        "result matched retry predicate",
                        rule.failureCode(),
                        rule.category()
                );
            }
        }
        return RetryDecision.success("operation completed successfully");
    }

    /** 最近 cause 优先；同一 cause 上选择继承距离最短的规则。 */
    private RetryExceptionRule findBestRule(
            List<RetryExceptionRule> rules,
            Throwable failure) {
        Throwable current = failure;
        Map<Throwable, Boolean> visited = new IdentityHashMap<>();
        int causeDepth = 0;
        while (current != null && causeDepth < maxCauseDepth) {
            if (visited.put(current, Boolean.TRUE) != null) {
                return null;
            }
            RetryExceptionRule bestRule = mostSpecificRule(rules, current.getClass());
            if (bestRule != null) {
                return bestRule;
            }
            if (!traverseCauses) {
                return null;
            }
            current = current.getCause();
            causeDepth++;
        }
        return null;
    }

    private static RetryExceptionRule mostSpecificRule(
            List<RetryExceptionRule> rules,
            Class<?> actualType) {
        RetryExceptionRule bestRule = null;
        int bestDistance = Integer.MAX_VALUE;
        for (RetryExceptionRule rule : rules) {
            int distance = inheritanceDistance(actualType, rule.exceptionType());
            if (distance >= 0 && distance < bestDistance) {
                bestRule = rule;
                bestDistance = distance;
            }
        }
        return bestRule;
    }

    private static int inheritanceDistance(Class<?> actualType, Class<?> configuredType) {
        int distance = 0;
        Class<?> current = actualType;
        while (current != null) {
            if (current == configuredType) {
                return distance;
            }
            current = current.getSuperclass();
            distance++;
        }
        return -1;
    }

    private static List<RetryExceptionRule> immutableCopy(
            List<RetryExceptionRule> source) {
        return Collections.unmodifiableList(new ArrayList<>(source));
    }
}

