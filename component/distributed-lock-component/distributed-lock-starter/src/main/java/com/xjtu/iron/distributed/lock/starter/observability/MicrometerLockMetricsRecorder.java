package com.xjtu.iron.distributed.lock.starter.observability;

import com.xjtu.iron.distributed.lock.api.status.LockStatus;
import com.xjtu.iron.distributed.lock.core.observability.LockMetricsRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

import java.time.Duration;
import java.util.Objects;

/** Micrometer 指标实现。 */
public final class MicrometerLockMetricsRecorder implements LockMetricsRecorder {
    private final MeterRegistry registry;
    public MicrometerLockMetricsRecorder(MeterRegistry registry) { this.registry = Objects.requireNonNull(registry, "registry must not be null"); }
    @Override
    public void recordAcquire(String provider, String namespace, String lockNamePattern, boolean success, Duration duration) {
        registry.timer("iron.lock.acquire", tags(provider, namespace, lockNamePattern).and("success", String.valueOf(success))).record(duration);
    }
    @Override
    public void recordHold(String provider, String namespace, String lockNamePattern, LockStatus status, Duration duration) {
        registry.timer("iron.lock.hold", tags(provider, namespace, lockNamePattern).and("status", status == null ? "UNKNOWN" : status.name())).record(duration);
    }
    @Override
    public void recordRenew(String provider, String namespace, boolean success) {
        registry.counter("iron.lock.renew", "provider", safe(provider), "namespace", safe(namespace), "success", String.valueOf(success)).increment();
    }
    @Override
    public void recordRelease(String provider, String namespace, boolean success) {
        registry.counter("iron.lock.release", "provider", safe(provider), "namespace", safe(namespace), "success", String.valueOf(success)).increment();
    }
    @Override
    public void recordLost(String provider, String namespace) {
        registry.counter("iron.lock.lost", "provider", safe(provider), "namespace", safe(namespace)).increment();
    }
    @Override
    public void recordFencing(String lockProvider, String fencingProvider, String namespace, boolean success, Duration duration) {
        registry.timer("iron.lock.fencing", "lock.provider", safe(lockProvider), "fencing.provider", safe(fencingProvider), "namespace",
                safe(namespace), "success", String.valueOf(success)).record(duration);
    }
    private static Tags tags(String provider, String namespace, String lock) { return Tags.of("provider", safe(provider), "namespace", safe(namespace), "lock", safe(lock)); }
    private static String safe(String value) { return value == null ? "unknown" : value; }
}
