package com.xjtu.iron.idempotent.starter.observation;

import com.xjtu.iron.idempotent.api.execution.IdempotencyResultStatus;
import com.xjtu.iron.idempotent.api.policy.IdempotencyMode;
import com.xjtu.iron.idempotent.core.observation.IdempotencyMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;

/**
 * Micrometer 指标实现。
 *
 * <p>这里只使用 mode / repository / status 作为标签。
 * {@code idempotencyKey}、{@code routeKey}、{@code ownerToken} 都是高基数值，
 * 如果放进 Prometheus label 会造成时间序列爆炸，因此明确禁止。</p>
 */
public final class MicrometerIdempotencyMetrics implements IdempotencyMetrics {

    private final MeterRegistry registry;

    public MicrometerIdempotencyMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordAcquire(IdempotencyMode mode, String repository, String status) {
        registry.counter("iron.idempotency.acquire", "mode", mode.name(), "repository", repository, "status", status)
                .increment();
    }

    @Override
    public void recordExecution(IdempotencyMode mode, String repository, IdempotencyResultStatus status, Duration duration) {
        Timer.builder("iron.idempotency.execution.duration")
                .tag("mode", mode.name())
                .tag("repository", repository)
                .tag("status", status.name())
                .register(registry)
                .record(duration);
    }
}
