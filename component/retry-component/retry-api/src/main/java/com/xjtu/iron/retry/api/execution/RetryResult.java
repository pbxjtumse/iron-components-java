package com.xjtu.iron.retry.api.execution;

import com.xjtu.iron.retry.api.exception.RetryExecutionException;
import com.xjtu.iron.retry.api.policy.RetryDecision;
import com.xjtu.iron.retry.api.policy.RetryFailureCategory;

import java.time.Duration;
import java.util.Objects;

/**
 * 描述一次逻辑重试执行的统一不可变结果。
 *
 * @param <T> 业务返回值类型
 */
public final class RetryResult<T> {

    /** 逻辑执行标识。 */
    private final String retryId;
    /** 稳定操作名称。 */
    private final String operationName;
    /** 策略名称。 */
    private final String policyName;
    /** 最终执行状态。 */
    private final RetryStatus status;
    /** 最后接受或保留的业务结果。 */
    private final T value;
    /** 最终失败原因。 */
    private final Throwable failure;
    /** 已经实际执行的尝试次数。 */
    private final int attempts;
    /** 整个逻辑执行消耗的单调时长。 */
    private final Duration elapsedTime;
    /** 最后一次已完成尝试。 */
    private final RetryAttempt<T> lastAttempt;
    /** 最后一次分类决策。 */
    private final RetryDecision lastDecision;

    private RetryResult(
            String retryId,
            String operationName,
            String policyName,
            RetryStatus status,
            T value,
            Throwable failure,
            int attempts,
            Duration elapsedTime,
            RetryAttempt<T> lastAttempt,
            RetryDecision lastDecision) {
        this.retryId = requireText(retryId, "retryId");
        this.operationName = requireText(operationName, "operationName");
        this.policyName = requireText(policyName, "policyName");
        this.status = Objects.requireNonNull(status, "status must not be null");
        if (attempts < 0) {
            throw new IllegalArgumentException("attempts must not be negative");
        }
        this.attempts = attempts;
        this.elapsedTime = requireNonNegative(elapsedTime, "elapsedTime");
        validateAttemptConsistency(attempts, lastAttempt, retryId, operationName, policyName);
        validateStatusConsistency(status, failure, lastAttempt, lastDecision);
        this.value = value;
        this.failure = failure;
        this.lastAttempt = lastAttempt;
        this.lastDecision = lastDecision;
    }

    /** 创建一个统一结果。 */
    public static <T> RetryResult<T> of(
            String retryId,
            String operationName,
            String policyName,
            RetryStatus status,
            T value,
            Throwable failure,
            int attempts,
            Duration elapsedTime,
            RetryAttempt<T> lastAttempt,
            RetryDecision lastDecision) {
        return new RetryResult<>(
                retryId,
                operationName,
                policyName,
                status,
                value,
                failure,
                attempts,
                elapsedTime,
                lastAttempt,
                lastDecision
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

    public RetryStatus getStatus() {
        return status;
    }

    public T getValue() {
        return value;
    }

    public Throwable getFailure() {
        return failure;
    }

    public int getAttempts() {
        return attempts;
    }

    public Duration getElapsedTime() {
        return elapsedTime;
    }

    public RetryAttempt<T> getLastAttempt() {
        return lastAttempt;
    }

    public RetryDecision getLastDecision() {
        return lastDecision;
    }

    public RetryFailureCategory getFailureCategory() {
        return lastDecision == null
                ? RetryFailureCategory.UNKNOWN
                : lastDecision.getFailureCategory();
    }

    public String getFailureCode() {
        return lastDecision == null ? "" : lastDecision.getFailureCode();
    }

    public boolean isSuccess() {
        return status == RetryStatus.SUCCESS;
    }

    public boolean isExhausted() {
        return status == RetryStatus.EXHAUSTED;
    }

    public T getOrThrow() {
        if (isSuccess()) {
            return value;
        }
        throw new RetryExecutionException(this);
    }

    /** 校验尝试次数和最后尝试快照保持一致。 */
    private static void validateAttemptConsistency(
            int attempts,
            RetryAttempt<?> lastAttempt,
            String retryId,
            String operationName,
            String policyName) {
        if (attempts == 0) {
            if (lastAttempt != null) {
                throw new IllegalArgumentException("lastAttempt must be null when attempts is zero");
            }
            return;
        }
        if (lastAttempt == null) {
            throw new IllegalArgumentException("lastAttempt is required when attempts is positive");
        }
        if (lastAttempt.getAttemptNumber() != attempts) {
            throw new IllegalArgumentException("lastAttempt number must equal attempts");
        }
        if (!lastAttempt.getRetryId().equals(retryId)
                || !lastAttempt.getOperationName().equals(operationName)
                || !lastAttempt.getPolicyName().equals(policyName)) {
            throw new IllegalArgumentException("lastAttempt must belong to the same execution");
        }
    }

    /** 校验状态、失败和决策之间没有明显矛盾。 */
    private static void validateStatusConsistency(
            RetryStatus status,
            Throwable failure,
            RetryAttempt<?> lastAttempt,
            RetryDecision lastDecision) {
        if (status == RetryStatus.SUCCESS && failure != null) {
            throw new IllegalArgumentException("SUCCESS result must not contain failure");
        }
        if (status == RetryStatus.SUCCESS
                && (lastDecision == null || !lastDecision.isSuccess())) {
            throw new IllegalArgumentException("SUCCESS result requires SUCCESS decision");
        }
        if (status == RetryStatus.INTERRUPTED && !(failure instanceof InterruptedException)) {
            throw new IllegalArgumentException(
                    "INTERRUPTED result requires InterruptedException failure"
            );
        }
        if (lastAttempt != null && failure == null && lastAttempt.hasFailure()) {
            throw new IllegalArgumentException(
                    "terminal failure must retain the last attempt failure"
            );
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
}
