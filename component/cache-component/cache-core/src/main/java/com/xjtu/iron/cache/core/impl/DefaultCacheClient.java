package com.xjtu.iron.cache.core.impl;

import com.xjtu.iron.cache.api.CacheClient;
import com.xjtu.iron.cache.api.enums.CacheDegradePolicy;
import com.xjtu.iron.cache.api.enums.CacheNullPolicy;
import com.xjtu.iron.cache.api.enums.CacheOperation;
import com.xjtu.iron.cache.api.exception.CacheException;
import com.xjtu.iron.cache.api.exception.CacheLoadException;
import com.xjtu.iron.cache.api.key.CacheKey;
import com.xjtu.iron.cache.api.loader.CacheLoader;
import com.xjtu.iron.cache.api.model.CacheResult;
import com.xjtu.iron.cache.api.model.CacheSpec;
import com.xjtu.iron.cache.api.model.CacheValue;
import com.xjtu.iron.cache.core.*;
import com.xjtu.iron.cache.core.event.CacheEvent;
import com.xjtu.iron.cache.core.event.CacheEventPublishFailurePolicy;
import com.xjtu.iron.cache.core.event.CacheEventPublisher;
import com.xjtu.iron.cache.core.trace.CacheTraceContext;

import java.time.Duration;
import java.util.Optional;

/**
 * 默认缓存客户端实现。
 *
 * <p>它是一期缓存组件的流程编排核心，但它不直接依赖 Caffeine 或 Redis。
 * 具体缓存能力通过 CacheProvider SPI 注入。</p>
 */
public class DefaultCacheClient implements CacheClient {

    /** 真正执行缓存读写的 Provider。一期默认是 CompositeCacheProvider。 */
    private final CacheProvider cacheProvider;

    /**
     * 缓存策略解析器。
     *
     * <p>根据 cacheName 解析 CacheSpec。</p>
     *
     * <p>一期来自 application.yml。</p>
     *
     * <p>二期可以升级为 RefreshableCacheSpecResolver。</p>
     */
    private final CacheSpecResolver specResolver;

    /** 负责计算正常值 TTL、空值 TTL 和 TTL 抖动。 */
    private final CacheTtlResolver ttlResolver;

    /**
     * 缓存加载保护器。
     *
     * <p>一期默认是 LocalMutexCacheLoadGuard。</p>
     *
     * <p>它只能解决单 JVM 内的并发击穿。</p>
     *
     * <p>分布式锁后面单独设计，不放在二期第一版。</p>
     */
    private final CacheLoadGuard loadGuard;

    /**
     * 缓存指标记录器。
     *
     * <p>如果项目里有 Micrometer，则使用 MicrometerCacheMetricsRecorder。</p>
     *
     * <p>否则使用 NoopCacheMetricsRecorder。</p>
     */
    private final CacheMetricsRecorder metricsRecorder;

    /**
     * 缓存事件发布器。
     *
     * <p>二期新增。</p>
     *
     * <p>用于 evict 后发布本地缓存失效事件。</p>
     *
     * <p>默认是 NoopCacheEventPublisher。</p>
     *
     * <p>开启 Redis Pub/Sub 后是 RedisCacheEventPublisher。</p>
     */
    private final CacheEventPublisher cacheEventPublisher;
    /**
     * 当前应用名。
     *
     * <p>用于事件来源标识。</p>
     */
    private final String applicationName;

    /**
     * 当前实例 ID。
     *
     * <p>用于 Redis Pub/Sub 事件去重。</p>
     *
     * <p>当前实例收到自己发布的事件时，可以忽略。</p>
     */
    private final String instanceId;

    /**
     * 事件发布失败策略。
     *
     * <p>用于控制 evict 后发布事件失败时是否影响主流程。</p>
     */
    private final CacheEventPublishFailurePolicy eventPublishFailurePolicy;

    /**
     * trace 上下文。
     *
     * <p>用于在缓存事件中携带 traceId / spanId。</p>
     */
    private final CacheTraceContext traceContext;

    /**
     * 事件发布失败策略。
     */

    /** 创建默认缓存客户端。 */
    public DefaultCacheClient(
            CacheProvider cacheProvider,
            CacheSpecResolver specResolver,
            CacheTtlResolver ttlResolver,
            CacheLoadGuard loadGuard,
            CacheMetricsRecorder metricsRecorder,
            CacheEventPublisher cacheEventPublisher,
            String applicationName,
            String instanceId,
            CacheEventPublishFailurePolicy eventPublishFailurePolicy,
            CacheTraceContext traceContext
    ) {
        this.cacheProvider = cacheProvider;
        this.specResolver = specResolver;
        this.ttlResolver = ttlResolver;
        this.loadGuard = loadGuard;
        this.metricsRecorder = metricsRecorder;
        this.cacheEventPublisher = cacheEventPublisher;
        this.applicationName = applicationName;
        this.instanceId = instanceId;
        this.eventPublishFailurePolicy = eventPublishFailurePolicy;
        this.traceContext = traceContext;
    }

    @Override
    public <T> CacheResult<T> get(CacheKey key, Class<T> valueType, CacheLoader<T> loader) {
        CacheSpec spec = specResolver.resolve(key);
        return get(key, valueType, spec, loader);
    }

    @Override
    public <T> CacheResult<T> get(CacheKey key, Class<T> valueType, CacheSpec spec, CacheLoader<T> loader) {
        long start = System.currentTimeMillis();

        CacheValue<T> cacheValue;

        try {
            cacheValue = cacheProvider.get(key, valueType, spec);
        } catch (Exception ex) {
            metricsRecorder.recordError(key, CacheOperation.GET, ex);
            return handleCacheReadException(key, spec, loader, start, ex);
        }

        if (cacheValue.isPresent()) {
            long cost = System.currentTimeMillis() - start;
            metricsRecorder.recordHit(key, cacheValue.getLevel(), cost);
            return CacheResult.hit(cacheValue, cost);
        }

        metricsRecorder.recordMiss(key, System.currentTimeMillis() - start);

        try {
            if (spec.isMutexLoad()) {
                return loadWithMutex(key, valueType, spec, loader, start);
            }

            return loadAndWrite(key, spec, loader, start);

        } catch (Exception ex) {
            if (ex instanceof CacheException cacheException) {
                throw cacheException;
            }

            throw new CacheLoadException("Cache load failed, key=" + key.fullKey(), ex);
        }
    }

    private <T> CacheResult<T> loadWithMutex(
            CacheKey key,
            Class<T> valueType,
            CacheSpec spec,
            CacheLoader<T> loader,
            long start
    ) throws Exception {
        return loadGuard.execute(key.fullKey(), () -> {
            CacheValue<T> recheckValue = cacheProvider.get(key, valueType, spec);

            if (recheckValue.isPresent()) {
                long cost = System.currentTimeMillis() - start;
                metricsRecorder.recordHit(key, recheckValue.getLevel(), cost);
                return CacheResult.hit(recheckValue, cost);
            }

            return loadAndWrite(key, spec, loader, start);
        });
    }

    private <T> CacheResult<T> loadAndWrite(
            CacheKey key,
            CacheSpec spec,
            CacheLoader<T> loader,
            long start
    ) {
        T loadedValue;

        try {
            loadedValue = loader.load();
        } catch (Exception ex) {
            metricsRecorder.recordError(key, CacheOperation.GET, ex);
            throw new CacheLoadException("Cache loader failed, key=" + key.fullKey(), ex);
        }

        try {
            if (loadedValue == null) {
                if (spec.getNullPolicy() == CacheNullPolicy.CACHE_NULL) {
                    Duration nullTtl = ttlResolver.resolveNullValueTtl(key, spec);
                    cacheProvider.putNullValue(key, nullTtl, spec);
                }
            } else {
                Duration ttl = ttlResolver.resolveNormalTtl(key, spec);
                cacheProvider.put(key, loadedValue, ttl, spec);
            }
        } catch (Exception ex) {
            metricsRecorder.recordError(key, CacheOperation.PUT, ex);
        }

        long cost = System.currentTimeMillis() - start;
        metricsRecorder.recordLoad(key, cost);

        return CacheResult.loaded(loadedValue, cost);
    }

    private <T> CacheResult<T> handleCacheReadException(
            CacheKey key,
            CacheSpec spec,
            CacheLoader<T> loader,
            long start,
            Exception ex
    ) {
        CacheDegradePolicy degradePolicy = spec.getDegradePolicy();

        if (degradePolicy == CacheDegradePolicy.RETURN_NULL) {
            return CacheResult.degraded(null, System.currentTimeMillis() - start);
        }

        if (degradePolicy == CacheDegradePolicy.LOAD_SOURCE) {
            try {
                T value = loader.load();
                return CacheResult.degraded(value, System.currentTimeMillis() - start);
            } catch (Exception loadEx) {
                throw new CacheLoadException(
                        "Cache degraded load source failed, key=" + key.fullKey(),
                        loadEx
                );
            }
        }

        throw new CacheException("Cache get failed, key=" + key.fullKey(), ex);
    }

    @Override
    public <T> Optional<T> getIfPresent(CacheKey key, Class<T> valueType) {
        CacheSpec spec = specResolver.resolve(key);
        CacheValue<T> value = cacheProvider.get(key, valueType, spec);

        if (!value.isPresent() || value.isNullValue()) {
            return Optional.empty();
        }

        return Optional.ofNullable(value.getValue());
    }

    @Override
    public void put(CacheKey key, Object value) {
        CacheSpec spec = specResolver.resolve(key);

        Duration ttl = value == null
                ? ttlResolver.resolveNullValueTtl(key, spec)
                : ttlResolver.resolveNormalTtl(key, spec);

        putInternal(key, value, ttl, spec);
    }

    @Override
    public void put(CacheKey key, Object value, Duration ttl) {
        CacheSpec spec = specResolver.resolve(key);
        putInternal(key, value, ttl, spec);
    }

    @Override
    public void put(CacheKey key, Object value, CacheSpec spec) {
        Duration ttl = value == null
                ? ttlResolver.resolveNullValueTtl(key, spec)
                : ttlResolver.resolveNormalTtl(key, spec);

        putInternal(key, value, ttl, spec);
    }

    private void putInternal(CacheKey key, Object value, Duration ttl, CacheSpec spec) {
        try {
            if (value == null) {
                if (spec.getNullPolicy() == CacheNullPolicy.SKIP_NULL) {
                    return;
                }

                cacheProvider.putNullValue(key, ttl, spec);
                return;
            }

            cacheProvider.put(key, value, ttl, spec);

        } catch (Exception ex) {
            metricsRecorder.recordError(key, CacheOperation.PUT, ex);
            throw new CacheException("Cache put failed, key=" + key.fullKey(), ex);
        }
    }

    @Override
    public void evict(CacheKey key) {
        try {
            /*
             * 一期已有能力：
             * 删除当前实例 L1 + Redis L2。
             */
            cacheProvider.evict(key);

            /*
             * 二期新增能力：
             * 发布 EVICT_KEY 事件。 其他实例收到事件后，只删除自己的 L1。
             */
            cacheEventPublisher.publish(CacheEvent.evictKey(key, applicationName, instanceId, "manual_evict"));

        } catch (Exception ex) {
            metricsRecorder.recordError(key, CacheOperation.EVICT, ex);
            throw new CacheException("Cache evict failed, key=" + key.fullKey(), ex);
        }

        /*
         * 增强流程：
         * 发布缓存失效事件，通知其他实例删除自己的 L1。
         *
         * 事件发布失败默认不影响主流程。
         */
        publishEvictEventSafely(key);
    }


    @Override
    public void refresh(CacheKey key, Class<?> valueType, CacheLoader<?> loader) {
        long start = System.currentTimeMillis();

        try {
            Object value = loader.load();
            put(key, value);
            metricsRecorder.recordLoad(key, System.currentTimeMillis() - start);
        } catch (Exception ex) {
            metricsRecorder.recordError(key, CacheOperation.REFRESH, ex);
            throw new CacheLoadException("Cache refresh failed, key=" + key.fullKey(), ex);
        }
    }

    @Override
    public void refresh(CacheKey key, Class<?> valueType, CacheSpec spec, CacheLoader<?> loader) {
        long start = System.currentTimeMillis();

        try {
            Object value = loader.load();
            put(key, value, spec);
            metricsRecorder.recordLoad(key, System.currentTimeMillis() - start);
        } catch (Exception ex) {
            metricsRecorder.recordError(key, CacheOperation.REFRESH, ex);
            throw new CacheLoadException("Cache refresh failed, key=" + key.fullKey(), ex);
        }
    }

    /**
     * 安全发布缓存失效事件。
     *
     * <p>这个方法的设计重点是：</p>
     *
     * <pre>
     * cacheProvider.evict 是主流程；
     * cacheEventPublisher.publish 是增强流程；
     * 增强流程失败时，默认不反向影响主流程。
     * </pre>
     */
    private void publishEvictEventSafely(CacheKey key) {
        try {
            CacheEvent event = CacheEvent.evictKey(
                    key,
                    applicationName,
                    instanceId,
                    "manual_evict",
                    traceContext.currentTraceId(),
                    traceContext.currentSpanId()
            );

            cacheEventPublisher.publish(event);

        } catch (Exception ex) {
            metricsRecorder.recordError(key, CacheOperation.PUBLISH_EVENT, ex);

            if (this.eventPublishFailurePolicy == CacheEventPublishFailurePolicy.THROW) {
                throw new CacheException("Cache evict event publish failed, key=" + key.fullKey(), ex);
            }

            /*
             * IGNORE 策略：
             * 只记录错误，不影响 cacheClient.evict 调用结果。
             */
        }
    }



}
