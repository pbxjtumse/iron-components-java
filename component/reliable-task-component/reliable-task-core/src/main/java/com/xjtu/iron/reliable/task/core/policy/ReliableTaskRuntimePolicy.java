package com.xjtu.iron.reliable.task.core.policy;

import java.time.Duration;
import java.util.Objects;

/** Reliable Task Core 的进程级默认策略。 */
public final class ReliableTaskRuntimePolicy {

    /** 提交任务未显式指定时采用的最大尝试次数。 */
    private final int defaultMaxAttempts;
    /** 每个存储域划分的扫描桶总数。 */
    private final int scanBucketCount;
    /** 每次成功抢占任务后授予执行者的租约时长。 */
    private final Duration leaseDuration;
    /** 处理器抛出未处理异常后的默认重试等待时长。 */
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
