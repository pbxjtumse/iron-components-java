package com.xjtu.iron.retry.api.policy;

import com.xjtu.iron.retry.api.backoff.BackoffStrategies;
import com.xjtu.iron.retry.api.backoff.BackoffStrategy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * 描述一次逻辑重试执行使用的不可变策略快照。
 *
 * <p>策略对象只保存可复用配置，不保存任何单次执行过程中的可变状态。</p>
 */
public final class RetryPolicy {

    /** 策略名称。 */
    private final String policyName;
    /** 最大尝试次数，包含第一次正常执行。 */
    private final int maxAttempts;
    /** 整个逻辑执行允许用于启动尝试和退避等待的最大时长。 */
    private final Duration maxDuration;
    /** 操作重复执行安全级别。 */
    private final OperationSafety operationSafety;
    /** 非幂等操作多次尝试时的安全处理模式。 */
    private final RetrySafetyMode safetyMode;
    /** 最终生效的分类器。 */
    private final RetryClassifier retryClassifier;
    /** 最终生效的退避策略。 */
    private final BackoffStrategy backoffStrategy;
    /** 是否遍历异常 cause 链。 */
    private final boolean traverseCauses;
    /** 遍历 cause 链时允许检查的最大深度。 */
    private final int maxCauseDepth;

    private RetryPolicy(Builder builder) {
        this.policyName = requireText(builder.policyName, "policyName");
        if (builder.maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be greater than or equal to 1");
        }
        this.maxAttempts = builder.maxAttempts;
        this.maxDuration = requirePositive(builder.maxDuration, "maxDuration");
        this.operationSafety = Objects.requireNonNull(
                builder.operationSafety,
                "operationSafety must not be null"
        );
        this.safetyMode = Objects.requireNonNull(
                builder.safetyMode,
                "safetyMode must not be null"
        );
        this.backoffStrategy = Objects.requireNonNull(
                builder.backoffStrategy,
                "backoffStrategy must not be null"
        );
        if (builder.maxCauseDepth < 1) {
            throw new IllegalArgumentException("maxCauseDepth must be greater than or equal to 1");
        }
        this.traverseCauses = builder.traverseCauses;
        this.maxCauseDepth = builder.maxCauseDepth;
        validateSafetyConfiguration();
        validateClassifierConfiguration(builder);
        validateExactRuleConflicts(builder.retryRules, builder.stopRules, builder.abortRules);
        this.retryClassifier = builder.customClassifier == null
                ? new RuleBasedRetryClassifier(
                        builder.retryRules,
                        builder.stopRules,
                        builder.abortRules,
                        builder.resultRules,
                        builder.traverseCauses,
                        builder.maxCauseDepth)
                : builder.customClassifier;
    }

    /** 创建命名策略构建器。 */
    public static Builder builder(String policyName) {
        return new Builder(policyName);
    }

    public String getPolicyName() {
        return policyName;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Duration getMaxDuration() {
        return maxDuration;
    }

    public OperationSafety getOperationSafety() {
        return operationSafety;
    }

    public RetrySafetyMode getSafetyMode() {
        return safetyMode;
    }

    public RetryClassifier getRetryClassifier() {
        return retryClassifier;
    }

    public BackoffStrategy getBackoffStrategy() {
        return backoffStrategy;
    }

    public boolean isTraverseCauses() {
        return traverseCauses;
    }

    public int getMaxCauseDepth() {
        return maxCauseDepth;
    }

    /** 判断执行器是否需要发布不安全重试告警。 */
    public boolean shouldWarnUnsafeRetry() {
        return operationSafety == OperationSafety.NON_IDEMPOTENT
                && maxAttempts > 1
                && safetyMode == RetrySafetyMode.WARN;
    }

    /** 校验非幂等操作与安全模式的组合。 */
    private void validateSafetyConfiguration() {
        if (operationSafety == OperationSafety.NON_IDEMPOTENT
                && maxAttempts > 1
                && safetyMode == RetrySafetyMode.REJECT) {
            throw new IllegalArgumentException(
                    "NON_IDEMPOTENT operation cannot configure maxAttempts > 1 "
                            + "when safetyMode is REJECT"
            );
        }
    }

    /** 禁止同时配置自定义分类器和声明式规则，避免规则被静默忽略。 */
    private static void validateClassifierConfiguration(Builder builder) {
        boolean hasConfiguredRules = !builder.retryRules.isEmpty()
                || !builder.stopRules.isEmpty()
                || !builder.abortRules.isEmpty()
                || !builder.resultRules.isEmpty();
        if (builder.customClassifier != null && hasConfiguredRules) {
            throw new IllegalArgumentException(
                    "custom classifier cannot be combined with retryOn, stopOn, abortOn "
                            + "or retryIfResult rules"
            );
        }
    }

    /** 拒绝同一个异常类型同时配置成不同动作。 */
    private static void validateExactRuleConflicts(
            List<RetryExceptionRule> retryRules,
            List<RetryExceptionRule> stopRules,
            List<RetryExceptionRule> abortRules) {
        Map<Class<? extends Throwable>, RetryDecisionType> actions = new java.util.HashMap<>();
        collectRuleActions(actions, retryRules, RetryDecisionType.RETRY);
        collectRuleActions(actions, stopRules, RetryDecisionType.STOP);
        collectRuleActions(actions, abortRules, RetryDecisionType.ABORT);
    }

    /** 收集精确异常类型与动作并检测冲突。 */
    private static void collectRuleActions(
            Map<Class<? extends Throwable>, RetryDecisionType> actions,
            List<RetryExceptionRule> rules,
            RetryDecisionType action) {
        for (RetryExceptionRule rule : rules) {
            RetryDecisionType previous = actions.putIfAbsent(rule.exceptionType(), action);
            if (previous != null && previous != action) {
                throw new IllegalArgumentException(
                        "exception type " + rule.exceptionType().getName()
                                + " is configured for both " + previous + " and " + action
                );
            }
        }
    }

    /** 提供链式策略配置。 */
    public static final class Builder {

        /** 必填策略名称。 */
        private final String policyName;
        /** 默认总共尝试三次。 */
        private int maxAttempts = 3;
        /** 默认逻辑执行预算为五秒。 */
        private Duration maxDuration = Duration.ofSeconds(5);
        /** 默认没有声明副作用安全性。 */
        private OperationSafety operationSafety = OperationSafety.UNSPECIFIED;
        /** 默认对不安全配置发布告警。 */
        private RetrySafetyMode safetyMode = RetrySafetyMode.WARN;
        /** 默认不退避。 */
        private BackoffStrategy backoffStrategy = BackoffStrategies.none();
        /** 默认不遍历 cause 链。 */
        private boolean traverseCauses;
        /** 默认最多检查十六层 cause。 */
        private int maxCauseDepth = 16;
        /** 声明式重试异常规则。 */
        private final List<RetryExceptionRule> retryRules = new ArrayList<>();
        /** 声明式停止异常规则。 */
        private final List<RetryExceptionRule> stopRules = new ArrayList<>();
        /** 声明式中止异常规则。 */
        private final List<RetryExceptionRule> abortRules = new ArrayList<>();
        /** 声明式结果规则。 */
        private final List<RetryResultRule> resultRules = new ArrayList<>();
        /** 可选完整自定义分类器。 */
        private RetryClassifier customClassifier;

        private Builder(String policyName) {
            this.policyName = policyName;
        }

        public Builder maxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
            return this;
        }

        public Builder maxDuration(Duration maxDuration) {
            this.maxDuration = maxDuration;
            return this;
        }

        public Builder operationSafety(OperationSafety operationSafety) {
            this.operationSafety = operationSafety;
            return this;
        }

        public Builder safetyMode(RetrySafetyMode safetyMode) {
            this.safetyMode = safetyMode;
            return this;
        }

        public Builder traverseCauses(boolean traverseCauses) {
            this.traverseCauses = traverseCauses;
            return this;
        }

        public Builder maxCauseDepth(int maxCauseDepth) {
            this.maxCauseDepth = maxCauseDepth;
            return this;
        }

        /** 使用默认瞬时失败分类和失败码添加可重试异常。 */
        @SafeVarargs
        @SuppressWarnings("varargs")
        public final Builder retryOn(Class<? extends Throwable>... exceptionTypes) {
            return retryOn(
                    RetryFailureCategory.TRANSIENT,
                    "RETRYABLE_EXCEPTION",
                    exceptionTypes
            );
        }

        /** 使用指定失败分类和失败码添加可重试异常。 */
        @SafeVarargs
        @SuppressWarnings("varargs")
        public final Builder retryOn(
                RetryFailureCategory category,
                String failureCode,
                Class<? extends Throwable>... exceptionTypes) {
            addRules(retryRules, category, failureCode, exceptionTypes, "retryOn");
            return this;
        }

        /** 添加正常停止异常。 */
        @SafeVarargs
        @SuppressWarnings("varargs")
        public final Builder stopOn(Class<? extends Throwable>... exceptionTypes) {
            addRules(
                    stopRules,
                    RetryFailureCategory.NON_RETRYABLE,
                    "NON_RETRYABLE_EXCEPTION",
                    exceptionTypes,
                    "stopOn"
            );
            return this;
        }

        /** 添加立即中止异常。 */
        @SafeVarargs
        @SuppressWarnings("varargs")
        public final Builder abortOn(Class<? extends Throwable>... exceptionTypes) {
            addRules(
                    abortRules,
                    RetryFailureCategory.NON_RETRYABLE,
                    "ABORT_EXCEPTION",
                    exceptionTypes,
                    "abortOn"
            );
            return this;
        }

        /** 使用默认结果未就绪分类添加结果重试条件。 */
        public Builder retryIfResult(Predicate<Object> predicate) {
            return retryIfResult(
                    predicate,
                    RetryFailureCategory.RESULT_NOT_READY,
                    "RESULT_NOT_READY"
            );
        }

        /** 使用指定失败分类和失败码添加结果重试条件。 */
        public Builder retryIfResult(
                Predicate<Object> predicate,
                RetryFailureCategory category,
                String failureCode) {
            resultRules.add(new RetryResultRule(
                    Objects.requireNonNull(predicate, "predicate must not be null"),
                    Objects.requireNonNull(category, "category must not be null"),
                    requireText(failureCode, "failureCode")
            ));
            return this;
        }

        public Builder classifier(RetryClassifier retryClassifier) {
            this.customClassifier = Objects.requireNonNull(
                    retryClassifier,
                    "retryClassifier must not be null"
            );
            return this;
        }

        public Builder backoffStrategy(BackoffStrategy backoffStrategy) {
            this.backoffStrategy = Objects.requireNonNull(
                    backoffStrategy,
                    "backoffStrategy must not be null"
            );
            return this;
        }

        /** 构建不可变策略。 */
        public RetryPolicy build() {
            return new RetryPolicy(this);
        }

        /** 向目标列表添加经过校验的异常规则。 */
        private static void addRules(
                List<RetryExceptionRule> target,
                RetryFailureCategory category,
                String failureCode,
                Class<? extends Throwable>[] exceptionTypes,
                String methodName) {
            RetryFailureCategory actualCategory = Objects.requireNonNull(
                    category,
                    "category must not be null"
            );
            if (actualCategory == RetryFailureCategory.NON_RETRYABLE
                    && "retryOn".equals(methodName)) {
                throw new IllegalArgumentException(
                        "retryOn cannot use NON_RETRYABLE failure category"
                );
            }
            String actualCode = requireText(failureCode, "failureCode");
            Objects.requireNonNull(
                    exceptionTypes,
                    methodName + " exceptionTypes must not be null"
            );
            if (exceptionTypes.length == 0) {
                throw new IllegalArgumentException(
                        methodName + " requires at least one exception type"
                );
            }
            for (Class<? extends Throwable> exceptionType : exceptionTypes) {
                Class<? extends Throwable> actualType = Objects.requireNonNull(
                        exceptionType,
                        methodName + " exceptionType must not be null"
                );
                target.add(new RetryExceptionRule(actualType, actualCategory, actualCode));
            }
        }
    }

    /** 校验文本非空且非空白。 */
    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    /** 校验 Duration 非空且为正数。 */
    private static Duration requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
