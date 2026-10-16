package com.xjtu.iron.retry.api.execution;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 描述一次物理尝试完成后的不可变快照。
 *
 * @param <T> 业务返回值类型
 */
public final class RetryAttempt<T> {

    /** 逻辑执行标识。 */
    private final String retryId;
    /** 稳定操作名称。 */
    private final String operationName;
    /** 策略名称。 */
    private final String policyName;
    /** 当前尝试编号，从 1 开始。 */
    private final int attemptNumber;
    /** 逻辑执行开始的墙上时间。 */
    private final Instant executionStartTime;
    /** 当前尝试开始的墙上时间。 */
    private final Instant attemptStartTime;
    /** 当前尝试完成的墙上时间。 */
    private final Instant completedAt;
    /** 当前尝试消耗的单调时长。 */
    private final Duration attemptDuration;
    /** 整个逻辑执行已经消耗的单调时长。 */
    private final Duration totalElapsedTime;
    /** 当前尝试完成后的剩余时长预算。 */
    private final Duration remainingDuration;
    /** 当前尝试返回的业务结果。 */
    private final T result;
    /** 当前尝试抛出的异常。 */
    private final Throwable failure;
    /** 调用方提供的只读上下文属性。 */
    private final Map<String, Object> attributes;

    public RetryAttempt(
            String retryId,
            String operationName,
            String policyName,
            int attemptNumber,
            Instant executionStartTime,
            Instant attemptStartTime,
            Instant completedAt,
            Duration attemptDuration,
            Duration totalElapsedTime,
            Duration remainingDuration,
            T result,
            Throwable failure,
            Map<String, Object> attributes) {
        this.retryId = requireText(retryId, "retryId");
        this.operationName = requireText(operationName, "operationName");
        this.policyName = requireText(policyName, "policyName");
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be greater than or equal to 1");
        }
        this.attemptNumber = attemptNumber;
        this.executionStartTime = Objects.requireNonNull(
                executionStartTime,
                "executionStartTime must not be null"
        );
        this.attemptStartTime = Objects.requireNonNull(
                attemptStartTime,
                "attemptStartTime must not be null"
        );
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt must not be null");
        this.attemptDuration = requireNonNegative(attemptDuration, "attemptDuration");
        this.totalElapsedTime = requireNonNegative(totalElapsedTime, "totalElapsedTime");
        this.remainingDuration = requireNonNegative(
                remainingDuration,
                "remainingDuration"
        );
        if (this.totalElapsedTime.compareTo(this.attemptDuration) < 0) {
            throw new IllegalArgumentException(
                    "totalElapsedTime must not be shorter than attemptDuration"
            );
        }
        if (result != null && failure != null) {
            throw new IllegalArgumentException("result and failure cannot both be non-null");
        }
        this.result = result;
        this.failure = failure;
        this.attributes = immutableAttributes(attributes);
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

    public Instant getExecutionStartTime() {
        return executionStartTime;
    }

    public Instant getAttemptStartTime() {
        return attemptStartTime;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Duration getAttemptDuration() {
        return attemptDuration;
    }

    public Duration getTotalElapsedTime() {
        return totalElapsedTime;
    }

    public Duration getRemainingDuration() {
        return remainingDuration;
    }

    public T getResult() {
        return result;
    }

    public Throwable getFailure() {
        return failure;
    }

    /** 判断当前尝试是否以异常结束。 */
    public boolean hasFailure() {
        return failure != null;
    }

    /** 判断当前尝试是否正常返回，包括返回 null。 */
    public boolean completedNormally() {
        return failure == null;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public Object getAttribute(String name) {
        return attributes.get(name);
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
