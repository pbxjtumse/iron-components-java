package com.xjtu.iron.retry.api.execution;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 传递给业务操作的当前尝试上下文。
 *
 * <p>RetryContext 描述即将开始的尝试，RetryAttempt 描述已经完成的尝试。</p>
 */
public final class RetryContext {

    /** 逻辑执行标识。 */
    private final String retryId;
    /** 稳定操作名称。 */
    private final String operationName;
    /** 策略名称。 */
    private final String policyName;
    /** 当前尝试编号，从 1 开始。 */
    private final int attemptNumber;
    /** 逻辑执行开始的墙上时间。 */
    private final Instant startTime;
    /** 逻辑执行已经消耗的单调时长。 */
    private final Duration elapsedTime;
    /** 当前剩余时长预算。 */
    private final Duration remainingDuration;
    /** 上一次已完成尝试，第一次尝试时为空。 */
    private final RetryAttempt<?> previousAttempt;
    /** 调用方提供的只读上下文属性。 */
    private final Map<String, Object> attributes;
    /** 协作式取消令牌。 */
    private final RetryCancellationToken cancellationToken;

    public RetryContext(
            String retryId,
            String operationName,
            String policyName,
            int attemptNumber,
            Instant startTime,
            Duration elapsedTime,
            Duration remainingDuration,
            RetryAttempt<?> previousAttempt,
            Map<String, Object> attributes,
            RetryCancellationToken cancellationToken) {
        this.retryId = requireText(retryId, "retryId");
        this.operationName = requireText(operationName, "operationName");
        this.policyName = requireText(policyName, "policyName");
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be greater than or equal to 1");
        }
        this.attemptNumber = attemptNumber;
        this.startTime = Objects.requireNonNull(startTime, "startTime must not be null");
        this.elapsedTime = requireNonNegative(elapsedTime, "elapsedTime");
        this.remainingDuration = requireNonNegative(
                remainingDuration,
                "remainingDuration"
        );
        validatePreviousAttempt(previousAttempt, attemptNumber, retryId, operationName, policyName);
        this.previousAttempt = previousAttempt;
        this.attributes = immutableAttributes(attributes);
        this.cancellationToken = Objects.requireNonNull(
                cancellationToken,
                "cancellationToken must not be null"
        );
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

    public int getRetryNumber() {
        return attemptNumber - 1;
    }

    public boolean isFirstAttempt() {
        return attemptNumber == 1;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Duration getElapsedTime() {
        return elapsedTime;
    }

    public Duration getRemainingDuration() {
        return remainingDuration;
    }

    public RetryAttempt<?> getPreviousAttempt() {
        return previousAttempt;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    public RetryCancellationToken getCancellationToken() {
        return cancellationToken;
    }

    public boolean isCancellationRequested() {
        return cancellationToken.isCancellationRequested();
    }

    /** 校验前一次尝试与当前上下文属于同一次逻辑执行。 */
    private static void validatePreviousAttempt(
            RetryAttempt<?> previousAttempt,
            int attemptNumber,
            String retryId,
            String operationName,
            String policyName) {
        if (previousAttempt == null) {
            if (attemptNumber != 1) {
                throw new IllegalArgumentException(
                        "previousAttempt is required when attemptNumber is greater than 1"
                );
            }
            return;
        }
        if (previousAttempt.getAttemptNumber() != attemptNumber - 1) {
            throw new IllegalArgumentException(
                    "previousAttempt number must immediately precede attemptNumber"
            );
        }
        if (!previousAttempt.getRetryId().equals(retryId)
                || !previousAttempt.getOperationName().equals(operationName)
                || !previousAttempt.getPolicyName().equals(policyName)) {
            throw new IllegalArgumentException(
                    "previousAttempt must belong to the same retry execution"
            );
        }
    }

    /** 浅复制调用方属性并返回不可修改的映射视图。 */
    private static Map<String, Object> immutableAttributes(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
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
}
