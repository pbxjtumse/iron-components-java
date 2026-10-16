package com.xjtu.iron.retry.api.event;

import com.xjtu.iron.retry.api.backoff.RetryDelay;
import com.xjtu.iron.retry.api.execution.RetryStatus;
import com.xjtu.iron.retry.api.policy.RetryDecision;
import com.xjtu.iron.retry.api.policy.RetryDecisionType;
import com.xjtu.iron.retry.api.policy.RetryFailureCategory;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 描述一个重试生命周期事件的不可变快照。
 *
 * <p>事件不保存完整业务请求体和返回值，只保留必要的标识、状态和失败信息。</p>
 */
public final class RetryEvent {

    /** 事件类型。 */
    private final RetryEventType eventType;
    /** 逻辑执行标识。 */
    private final String retryId;
    /** 稳定操作名称。 */
    private final String operationName;
    /** 策略名称。 */
    private final String policyName;
    /** 当前尝试编号，执行级事件可以为 0。 */
    private final int attemptNumber;
    /** 最大尝试次数。 */
    private final int maxAttempts;
    /** 事件发生的墙上时间。 */
    private final Instant occurredAt;
    /** 整个逻辑执行累计耗时。 */
    private final Duration elapsedTime;
    /** 当前尝试耗时。 */
    private final Duration attemptDuration;
    /** 可选决策动作。 */
    private final RetryDecisionType decisionType;
    /** 决策或告警原因。 */
    private final String decisionReason;
    /** 稳定失败码。 */
    private final String failureCode;
    /** 统一失败分类。 */
    private final RetryFailureCategory failureCategory;
    /** 退避等待结果。 */
    private final RetryDelay retryDelay;
    /** 可选最终状态。 */
    private final RetryStatus finalStatus;
    /** 可选失败对象。 */
    private final Throwable failure;

    private RetryEvent(Builder builder) {
        this.eventType = Objects.requireNonNull(builder.eventType, "eventType must not be null");
        this.retryId = requireText(builder.retryId, "retryId");
        this.operationName = requireText(builder.operationName, "operationName");
        this.policyName = requireText(builder.policyName, "policyName");
        if (builder.attemptNumber < 0) {
            throw new IllegalArgumentException("attemptNumber must not be negative");
        }
        if (builder.maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be greater than or equal to 1");
        }
        this.attemptNumber = builder.attemptNumber;
        this.maxAttempts = builder.maxAttempts;
        this.occurredAt = Objects.requireNonNull(builder.occurredAt, "occurredAt must not be null");
        this.elapsedTime = requireNonNegative(builder.elapsedTime, "elapsedTime");
        this.attemptDuration = requireNonNegative(
                builder.attemptDuration,
                "attemptDuration"
        );
        this.decisionType = builder.decisionType;
        this.decisionReason = normalize(builder.decisionReason);
        this.failureCode = normalize(builder.failureCode);
        this.failureCategory = Objects.requireNonNull(
                builder.failureCategory,
                "failureCategory must not be null"
        );
        this.retryDelay = Objects.requireNonNull(builder.retryDelay, "retryDelay must not be null");
        this.finalStatus = builder.finalStatus;
        this.failure = builder.failure;
    }

    /** 创建事件构建器。 */
    public static Builder builder(
            RetryEventType eventType,
            String retryId,
            String operationName,
            String policyName,
            int maxAttempts,
            Instant occurredAt) {
        return new Builder(
                eventType,
                retryId,
                operationName,
                policyName,
                maxAttempts,
                occurredAt
        );
    }

    public RetryEventType getEventType() {
        return eventType;
    }

    public String getRetryId() {
        return retryId;
    }

    public String getOperationName() {
        return operationName;
    }

    public String getPolicyName() {
        return policyName;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Duration getElapsedTime() {
        return elapsedTime;
    }

    public Duration getAttemptDuration() {
        return attemptDuration;
    }

    public RetryDecisionType getDecisionType() {
        return decisionType;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public RetryFailureCategory getFailureCategory() {
        return failureCategory;
    }

    public RetryDelay getRetryDelay() {
        return retryDelay;
    }

    public RetryStatus getFinalStatus() {
        return finalStatus;
    }

    public Throwable getFailure() {
        return failure;
    }

    public String getFailureType() {
        return failure == null ? "" : failure.getClass().getName();
    }

    public String getFailureMessage() {
        return failure == null || failure.getMessage() == null ? "" : failure.getMessage();
    }

    /** 构建 RetryEvent 的可变构建器。 */
    public static final class Builder {

        /** 必填事件类型。 */
        private final RetryEventType eventType;
        /** 必填逻辑执行标识。 */
        private final String retryId;
        /** 必填稳定操作名称。 */
        private final String operationName;
        /** 必填策略名称。 */
        private final String policyName;
        /** 必填最大尝试次数。 */
        private final int maxAttempts;
        /** 必填事件时间。 */
        private final Instant occurredAt;
        /** 默认执行级事件没有尝试编号。 */
        private int attemptNumber;
        /** 默认累计耗时为零。 */
        private Duration elapsedTime = Duration.ZERO;
        /** 默认尝试耗时为零。 */
        private Duration attemptDuration = Duration.ZERO;
        /** 可选决策动作。 */
        private RetryDecisionType decisionType;
        /** 可选决策原因。 */
        private String decisionReason = "";
        /** 可选稳定失败码。 */
        private String failureCode = "";
        /** 默认失败分类未知。 */
        private RetryFailureCategory failureCategory = RetryFailureCategory.UNKNOWN;
        /** 默认不等待。 */
        private RetryDelay retryDelay = RetryDelay.none();
        /** 可选最终状态。 */
        private RetryStatus finalStatus;
        /** 可选失败对象。 */
        private Throwable failure;

        private Builder(
                RetryEventType eventType,
                String retryId,
                String operationName,
                String policyName,
                int maxAttempts,
                Instant occurredAt) {
            this.eventType = eventType;
            this.retryId = retryId;
            this.operationName = operationName;
            this.policyName = policyName;
            this.maxAttempts = maxAttempts;
            this.occurredAt = occurredAt;
        }

        public Builder attemptNumber(int attemptNumber) {
            this.attemptNumber = attemptNumber;
            return this;
        }

        public Builder elapsedTime(Duration elapsedTime) {
            this.elapsedTime = elapsedTime;
            return this;
        }

        public Builder attemptDuration(Duration attemptDuration) {
            this.attemptDuration = attemptDuration;
            return this;
        }

        /** 从重试决策复制动作、原因、失败码和失败分类。 */
        public Builder decision(RetryDecision decision) {
            if (decision == null) {
                return this;
            }
            this.decisionType = decision.getType();
            this.decisionReason = decision.getReason();
            this.failureCode = decision.getFailureCode();
            this.failureCategory = decision.getFailureCategory();
            return this;
        }

        public Builder notice(
                String reason,
                String failureCode,
                RetryFailureCategory failureCategory) {
            this.decisionReason = reason;
            this.failureCode = failureCode;
            this.failureCategory = failureCategory;
            return this;
        }

        public Builder retryDelay(RetryDelay retryDelay) {
            this.retryDelay = retryDelay;
            return this;
        }

        public Builder finalStatus(RetryStatus finalStatus) {
            this.finalStatus = finalStatus;
            return this;
        }

        public Builder failure(Throwable failure) {
            this.failure = failure;
            return this;
        }

        /** 创建不可变事件。 */
        public RetryEvent build() {
            return new RetryEvent(this);
        }
    }

    /** 校验文本非空且非空白。 */
    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    /** 校验 Duration 非空且非负。 */
    private static Duration requireNonNegative(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    /** 将可选文本规范化为非空字符串。 */
    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
