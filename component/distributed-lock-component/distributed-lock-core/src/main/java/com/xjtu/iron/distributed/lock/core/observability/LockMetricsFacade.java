package com.xjtu.iron.distributed.lock.core.observability;

import com.xjtu.iron.distributed.lock.api.status.LockStatus;
import com.xjtu.iron.distributed.lock.core.support.LockNamePatternResolver;
import com.xjtu.iron.distributed.lock.spi.protocol.common.LockLease;

import java.time.Duration;
import java.util.Objects;

/**
 * 指标门面，统一做 lockName pattern 归一化，避免各处直接处理高基数标签。
 */
public final class LockMetricsFacade {

    private final LockMetricsRecorder recorder;
    private final LockNamePatternResolver patternResolver;

    public LockMetricsFacade(LockMetricsRecorder recorder, LockNamePatternResolver patternResolver) {
        this.recorder = Objects.requireNonNull(recorder, "recorder must not be null");
        this.patternResolver = Objects.requireNonNull(patternResolver, "patternResolver must not be null");
    }

    public void recordAcquire(String provider, String namespace, String lockName, boolean success, Duration duration) {
        recorder.recordAcquire(provider, namespace, patternResolver.resolvePattern(lockName), success, duration);
    }

    public void recordHold(LockLease lease, LockStatus status, Duration duration) {
        recorder.recordHold(lease.getProviderName(), lease.getNamespace(), patternResolver.resolvePattern(lease.getLockName()), status, duration);
    }

    public void recordRenew(LockLease lease, boolean success) {
        recorder.recordRenew(lease.getProviderName(), lease.getNamespace(), success);
    }

    public void recordRelease(LockLease lease, boolean success) {
        recorder.recordRelease(lease.getProviderName(), lease.getNamespace(), success);
    }

    public void recordLost(LockLease lease) {
        recorder.recordLost(lease.getProviderName(), lease.getNamespace());
    }

    public void recordFencing(String lockProvider, String fencingProvider, String namespace, boolean success, Duration duration) {
        recorder.recordFencing(lockProvider, fencingProvider, namespace, success, duration);
    }
}
