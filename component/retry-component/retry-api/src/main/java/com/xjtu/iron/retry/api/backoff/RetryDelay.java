package com.xjtu.iron.retry.api.backoff;

import java.time.Duration;
import java.util.Objects;

/**
 * 描述退避策略计算出的等待时间、来源和解释信息。
 *
 * <p>零等待也会保留来源，便于区分“没有配置退避”和“协议明确要求立即重试”。</p>
 */
public final class RetryDelay {

    private static final RetryDelay NONE =
            new RetryDelay(Duration.ZERO, RetryDelaySource.NONE, "no delay");

    /** 实际等待时长。 */
    private final Duration duration;
    /** 等待时长的来源。 */
    private final RetryDelaySource source;
    /** 便于日志和事件解释的原因。 */
    private final String reason;

    private RetryDelay(Duration duration, RetryDelaySource source, String reason) {
        this.duration = requireNonNegative(duration, "duration");
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.reason = normalize(reason);
        if (source == RetryDelaySource.NONE && !duration.isZero()) {
            throw new IllegalArgumentException("NONE delay source requires zero duration");
        }
    }

    /** 返回没有退避等待的共享对象。 */
    public static RetryDelay none() {
        return NONE;
    }

    /** 创建一个保留来源信息的退避结果。 */
    public static RetryDelay of(Duration duration, RetryDelaySource source, String reason) {
        Duration actualDuration = requireNonNegative(duration, "duration");
        RetryDelaySource actualSource = Objects.requireNonNull(source, "source must not be null");
        if (actualDuration.isZero() && actualSource == RetryDelaySource.NONE) {
            return NONE;
        }
        if (actualSource == RetryDelaySource.NONE) {
            throw new IllegalArgumentException("non-NONE source is required for explicit delay");
        }
        return new RetryDelay(actualDuration, actualSource, reason);
    }

    public Duration getDuration() {
        return duration;
    }

    public RetryDelaySource getSource() {
        return source;
    }

    public String getReason() {
        return reason;
    }

    public boolean isZero() {
        return duration.isZero();
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
