package com.xjtu.iron.retry.api.policy;

import com.xjtu.iron.retry.api.backoff.RetryDelaySource;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * 描述分类器对一次物理尝试做出的不可变决策。
 *
 * <p>除了动作类型，决策还携带稳定失败码、统一失败分类、原因以及可选等待时间覆盖。</p>
 */
public final class RetryDecision {

    /** 决策动作。 */
    private final RetryDecisionType type;
    /** 面向开发和观测的解释文本。 */
    private final String reason;
    /** 面向监控和上层集成的稳定失败码。 */
    private final String failureCode;
    /** 跨协议统一的失败分类。 */
    private final RetryFailureCategory failureCategory;
    /** 可选的退避时间覆盖。 */
    private final Duration delayOverride;
    /** 退避时间覆盖的来源。 */
    private final RetryDelaySource delaySource;

    private RetryDecision(
            RetryDecisionType type,
            String reason,
            String failureCode,
            RetryFailureCategory failureCategory,
            Duration delayOverride,
            RetryDelaySource delaySource) {
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.reason = requireText(reason, "reason");
        this.failureCategory = Objects.requireNonNull(
                failureCategory,
                "failureCategory must not be null"
        );
        this.delayOverride = validateDelay(type, delayOverride);
        this.delaySource = validateDelaySource(delayOverride, delaySource);
        this.failureCode = validateFailureCode(type, failureCode);
        validateCategory(type, failureCategory);
    }

    /** 创建接受当前结果的成功决策。 */
    public static RetryDecision success(String reason) {
        return new RetryDecision(
                RetryDecisionType.SUCCESS,
                reason,
                "",
                RetryFailureCategory.UNKNOWN,
                null,
                RetryDelaySource.NONE
        );
    }

    /** 创建使用策略默认退避的重试决策。 */
    public static RetryDecision retry(
            String reason,
            String failureCode,
            RetryFailureCategory failureCategory) {
        return new RetryDecision(
                RetryDecisionType.RETRY,
                reason,
                failureCode,
                failureCategory,
                null,
                RetryDelaySource.NONE
        );
    }

    /** 创建覆盖策略默认退避的重试决策。 */
    public static RetryDecision retryAfter(
            Duration delay,
            RetryDelaySource delaySource,
            String reason,
            String failureCode,
            RetryFailureCategory failureCategory) {
        return new RetryDecision(
                RetryDecisionType.RETRY,
                reason,
                failureCode,
                failureCategory,
                Objects.requireNonNull(delay, "delay must not be null"),
                delaySource
        );
    }

    /** 创建不可重试但属于正常业务终止的停止决策。 */
    public static RetryDecision stop(
            String reason,
            String failureCode,
            RetryFailureCategory failureCategory) {
        return new RetryDecision(
                RetryDecisionType.STOP,
                reason,
                failureCode,
                failureCategory,
                null,
                RetryDelaySource.NONE
        );
    }

    /** 创建要求立即终止的中止决策。 */
    public static RetryDecision abort(String reason, String failureCode) {
        return new RetryDecision(
                RetryDecisionType.ABORT,
                reason,
                failureCode,
                RetryFailureCategory.NON_RETRYABLE,
                null,
                RetryDelaySource.NONE
        );
    }

    public RetryDecisionType getType() {
        return type;
    }

    public String getReason() {
        return reason;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public RetryFailureCategory getFailureCategory() {
        return failureCategory;
    }

    public Optional<Duration> getDelayOverride() {
        return Optional.ofNullable(delayOverride);
    }

    public RetryDelaySource getDelaySource() {
        return delaySource;
    }

    public boolean isSuccess() {
        return type == RetryDecisionType.SUCCESS;
    }

    public boolean isRetry() {
        return type == RetryDecisionType.RETRY;
    }

    /** 校验可选等待时间只用于重试决策。 */
    private static Duration validateDelay(RetryDecisionType type, Duration delayOverride) {
        if (delayOverride == null) {
            return null;
        }
        if (delayOverride.isNegative()) {
            throw new IllegalArgumentException("delayOverride must not be negative");
        }
        if (type != RetryDecisionType.RETRY) {
            throw new IllegalArgumentException("delayOverride is only valid for RETRY decisions");
        }
        return delayOverride;
    }

    /** 校验等待来源与等待覆盖是否一致。 */
    private static RetryDelaySource validateDelaySource(
            Duration delayOverride,
            RetryDelaySource delaySource) {
        if (delayOverride == null) {
            return RetryDelaySource.NONE;
        }
        RetryDelaySource actualSource = Objects.requireNonNull(
                delaySource,
                "delaySource must not be null when delayOverride exists"
        );
        if (actualSource == RetryDelaySource.NONE) {
            throw new IllegalArgumentException(
                    "delaySource must not be NONE when delayOverride exists"
            );
        }
        return actualSource;
    }

    /** 校验不同决策类型对失败码的约束。 */
    private static String validateFailureCode(
            RetryDecisionType type,
            String failureCode) {
        if (type == RetryDecisionType.SUCCESS) {
            return failureCode == null ? "" : failureCode.trim();
        }
        return requireText(failureCode, "failureCode");
    }

    /** 校验失败分类与决策动作之间没有明显矛盾。 */
    private static void validateCategory(
            RetryDecisionType type,
            RetryFailureCategory failureCategory) {
        if (type == RetryDecisionType.RETRY
                && failureCategory == RetryFailureCategory.NON_RETRYABLE) {
            throw new IllegalArgumentException(
                    "RETRY decision cannot use NON_RETRYABLE failure category"
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
}
