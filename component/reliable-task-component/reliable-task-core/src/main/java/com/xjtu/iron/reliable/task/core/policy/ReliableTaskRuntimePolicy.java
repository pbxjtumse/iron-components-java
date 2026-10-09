package com.xjtu.iron.reliable.task.core.policy;

import java.time.Duration;
import java.util.Objects;

/** Reliable Task Core 的进程级默认策略。 */
public final class ReliableTaskRuntimePolicy {

    private final int defaultMaxAttempts;
    private final int scanBucketCount;
    private final Duration leaseDuration;
    private final Duration failureRetryDelay;

    public ReliableTaskRuntimePolicy(
            int defaultMaxAttempts,
            int scanBucketCount,
            Duration leaseDuration,
            Duration failureRetryDelay) {
        if (defaultMaxAttempts < 1) {
            throw new IllegalArgumentException("defaultMaxAttempts must be greater than zero");
        }
        if (scanBucketCount < 1) {
            throw new IllegalArgumentException("scanBucketCount must be greater than zero");
        }
        this.defaultMaxAttempts = defaultMaxAttempts;
        this.scanBucketCount = scanBucketCount;
        this.leaseDuration = requirePositive(leaseDuration, "leaseDuration");
        this.failureRetryDelay = requirePositive(failureRetryDelay, "failureRetryDelay");
    }

    public static ReliableTaskRuntimePolicy defaults() {
        return new ReliableTaskRuntimePolicy(10, 64, Duration.ofMinutes(1), Duration.ofMinutes(1));
    }

    public int getDefaultMaxAttempts() { return defaultMaxAttempts; }
    public int getScanBucketCount() { return scanBucketCount; }
    public Duration getLeaseDuration() { return leaseDuration; }
    public Duration getFailureRetryDelay() { return failureRetryDelay; }

    private static Duration requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
