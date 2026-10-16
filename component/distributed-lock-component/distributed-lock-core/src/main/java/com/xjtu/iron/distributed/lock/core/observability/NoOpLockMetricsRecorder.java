package com.xjtu.iron.distributed.lock.core.observability;

import com.xjtu.iron.distributed.lock.api.status.LockStatus;

import java.time.Duration;

/**
 * 空指标记录器。
 */
public final class NoOpLockMetricsRecorder implements LockMetricsRecorder {

    @Override public void recordAcquire(String provider, String namespace, String lockNamePattern, boolean success, Duration duration) { }
    @Override public void recordHold(String provider, String namespace, String lockNamePattern, LockStatus status, Duration duration) { }
    @Override public void recordRenew(String provider, String namespace, boolean success) { }
    @Override public void recordRelease(String provider, String namespace, boolean success) { }
    @Override public void recordLost(String provider, String namespace) { }
}
