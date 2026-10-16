package com.xjtu.iron.cache.provider.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.xjtu.iron.cache.api.key.CacheKey;
import com.xjtu.iron.cache.api.model.CacheSpec;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caffeine 本地缓存管理器。
 *
 * <p>这个类不是通用 CacheManager。</p>
 *
 * <p>它只负责管理当前 JVM 内的 Caffeine 缓存实例。</p>
 *
 * <p>设计说明：</p>
 *
 * <pre>
 * 1. 每个 cacheName 对应一个 Caffeine Cache；
 * 2. 不同 cacheName 之间容量和数据隔离；
 * 3. 支持按 cacheName 清理本地缓存；
 * 4. 支持按 CacheKey 删除本地缓存；
 * 5. 不操作 Redis；
 * 6. 不负责多级缓存编排；
 * 7. 不负责执行 loader。
 * </pre>
 *
 * <p>多级缓存编排由 CompositeCacheProvider 负责。</p>
 */
public class CaffeineLocalCacheManager {

    /**
     * 默认本地缓存最大容量。
     *
     * <p>一期先做全局容量配置。</p>
     *
     * <p>后续可以扩展为每个 cacheName 独立容量配置。</p>
     */
    private final long defaultMaximumSize;

    /**
     * 本地缓存实例集合。
     *
     * <p>key 是 cacheName。</p>
     *
     * <p>value 是该 cacheName 对应的 Caffeine Cache。</p>
     */
    private final Map<String, Cache<String, LocalCacheEntry>> caches = new ConcurrentHashMap<>();

    public CaffeineLocalCacheManager(long defaultMaximumSize) {
        this.defaultMaximumSize = defaultMaximumSize;
    }

    /**
     * 根据 cacheName 获取 Caffeine Cache。
     *
     * <p>如果不存在，则创建一个新的 Caffeine Cache。</p>
     *
     * <p>注意：</p>
     *
     * <pre>
     * 当前没有直接使用 Caffeine expireAfterWrite，
     * 因为 LocalCacheEntry 内部会维护每个 key 的 expireAtMillis。
     * 这样可以支持：
     * 1. 每个 key 独立 TTL；
     * 2. TTL 抖动；
     * 3. 空值 TTL；
     * 4. Redis 回填 L1 时使用不同 TTL。
     * </pre>
     */
    public Cache<String, LocalCacheEntry> getCache(String cacheName, CacheSpec spec) {
        return caches.computeIfAbsent(cacheName, ignored -> Caffeine.newBuilder()
                .maximumSize(defaultMaximumSize)
                .recordStats()
                .build());
    }

    /**
     * 删除当前 JVM 本地缓存中的单个 key。
     *
     * <p>只删除 Caffeine，不删除 Redis。</p>
     *
     * <p>二期 Redis Pub/Sub 收到其他实例的失效事件时，会调用这个方法。</p>
     */
    public void evict(CacheKey key) {
        Cache<String, LocalCacheEntry> cache = caches.get(key.cacheName());

        if (cache != null) {
            cache.invalidate(key.fullKey());
        }
    }

    /**
     * 清理某个 cacheName 下的全部本地缓存。
     *
     * <p>二期第一版可以先不用，后续支持 CLEAR_CACHE_NAME 事件时使用。</p>
     */
    public void clear(String cacheName) {
        Cache<String, LocalCacheEntry> cache = caches.get(cacheName);

        if (cache != null) {
            cache.invalidateAll();
        }
    }

    /**
     * 清理当前 JVM 内所有 Caffeine 本地缓存。
     *
     * <p>一般只用于测试、运维接口或极端降级场景。</p>
     */
    public void clearAll() {
        for (Cache<String, LocalCacheEntry> cache : caches.values()) {
            cache.invalidateAll();
        }
    }
}