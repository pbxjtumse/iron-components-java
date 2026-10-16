package com.xjtu.iron.cache.core.impl;

import com.xjtu.iron.cache.api.key.CacheKey;
import com.xjtu.iron.cache.api.model.CacheSpec;
import com.xjtu.iron.cache.core.CacheTtlResolver;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 默认 TTL 解析器。
 *
 * <p>一期、二期默认使用这个实现。</p>
 *
 * <p>核心能力：</p>
 *
 * <pre>
 * 1. 正常值 TTL；
 * 2. 空值 TTL；
 * 3. TTL 随机抖动，避免大量 key 同一时间过期造成缓存雪崩；
 * 4. TTL 兜底，避免配置为空或非法时导致缓存不可用。
 * </pre>
 *
 * <p>注意：</p>
 *
 * <pre>
 * 这里不会区分 Redis TTL 和 Caffeine TTL。
 * 上层 core 计算出 TTL 后，传给各个 Provider。
 * </pre>
 */
public class DefaultCacheTtlResolver implements CacheTtlResolver {

    private static final Duration DEFAULT_NORMAL_TTL = Duration.ofMinutes(10);

    private static final Duration DEFAULT_NULL_VALUE_TTL = Duration.ofSeconds(30);

    private static final Duration MIN_TTL = Duration.ofSeconds(1);

    @Override
    public Duration resolveNormalTtl(CacheKey key, CacheSpec spec) {
        Duration ttl = safeDuration(spec.getTtl(), DEFAULT_NORMAL_TTL);
        Duration jitter = safeDuration(spec.getTtlJitter(), Duration.ZERO);

        if (jitter.isZero() || jitter.isNegative()) {
            return ttl;
        }

        long baseMillis = ttl.toMillis();
        long jitterMillis = jitter.toMillis();

        /*
         * 生成 [-jitter, +jitter] 区间的随机偏移。
         *
         * 例如：
         * ttl = 5m
         * jitter = 60s
         *
         * 最终 TTL 可能是 4m10s，也可能是 5m40s。
         */
        long offsetMillis = ThreadLocalRandom.current()
                .nextLong(-jitterMillis, jitterMillis + 1);

        long finalMillis = Math.max(MIN_TTL.toMillis(), baseMillis + offsetMillis);

        return Duration.ofMillis(finalMillis);
    }

    @Override
    public Duration resolveNullValueTtl(CacheKey key, CacheSpec spec) {
        return safeDuration(spec.getNullValueTtl(), DEFAULT_NULL_VALUE_TTL);
    }

    /**
     * 对 Duration 做安全兜底。
     *
     * <p>如果配置为空、0 或负数，则使用默认值。</p>
     */
    private Duration safeDuration(Duration duration, Duration defaultValue) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return defaultValue;
        }

        return duration;
    }
}