package com.xjtu.iron.cache.integrations.observability;

import com.xjtu.iron.cache.api.enums.CacheLevel;
import com.xjtu.iron.cache.api.enums.CacheOperation;
import com.xjtu.iron.cache.api.key.CacheKey;
import com.xjtu.iron.cache.core.CacheMetricsRecorder;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Micrometer 的缓存指标记录器。
 *
 * <p>注意：指标标签只放 cacheName、level、operation 等低基数字段，不放 fullKey，
 * 避免 Prometheus 等指标系统出现高基数问题。</p>
 */
public class MicrometerCacheMetricsRecorder implements CacheMetricsRecorder {

    /** Micrometer 指标注册中心。 */
    private final MeterRegistry meterRegistry;

    /** 创建 Micrometer 指标记录器。 */
    public MicrometerCacheMetricsRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /** 记录缓存命中次数和 get 耗时。 */
    @Override
    public void recordHit(CacheKey key, CacheLevel level, long costMillis) {
        meterRegistry.counter("xjtu.iron.cache.hit", "cacheName", key.cacheName(), "level", level.name()).increment();
        meterRegistry.timer("xjtu.iron.cache.get.cost", "cacheName", key.cacheName(), "result", "hit", "level", level.name())
                .record(costMillis, TimeUnit.MILLISECONDS);
    }

    /** 记录缓存 miss 次数和 get 耗时。 */
    @Override
    public void recordMiss(CacheKey key, long costMillis) {
        meterRegistry.counter("xjtu.iron.cache.miss", "cacheName", key.cacheName()).increment();
        meterRegistry.timer("xjtu.iron.cache.get.cost", "cacheName", key.cacheName(), "result", "miss")
                .record(costMillis, TimeUnit.MILLISECONDS);
    }

    /** 记录 loader 加载次数和耗时。 */
    @Override
    public void recordLoad(CacheKey key, long costMillis) {
        meterRegistry.counter("xjtu.iron.cache.loader.count", "cacheName", key.cacheName()).increment();
        meterRegistry.timer("xjtu.iron.cache.loader.cost", "cacheName", key.cacheName())
                .record(costMillis, TimeUnit.MILLISECONDS);
    }

    /** 记录缓存操作异常。 */
    @Override
    public void recordError(CacheKey key, CacheOperation operation, Throwable throwable) {
        meterRegistry.counter("xjtu.iron.cache.error", "cacheName", key.cacheName(), "operation", operation.name(),
                "exception", throwable.getClass().getSimpleName()).increment();
    }
}
