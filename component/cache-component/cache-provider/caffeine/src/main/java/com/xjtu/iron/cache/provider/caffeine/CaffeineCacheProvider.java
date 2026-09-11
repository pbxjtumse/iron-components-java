package com.xjtu.iron.cache.provider.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.xjtu.iron.cache.api.enums.CacheLevel;
import com.xjtu.iron.cache.api.key.CacheKey;
import com.xjtu.iron.cache.api.model.CacheSpec;
import com.xjtu.iron.cache.api.model.CacheValue;
import com.xjtu.iron.cache.core.CacheProvider;

import java.time.Duration;

/**
 * Caffeine 本地缓存 Provider。
 *
 * <p>这个类是 L1 本地缓存的具体实现。业务代码不会直接使用它，业务只依赖 CacheClient。</p>
 *
 * <p>为什么类名里保留 Caffeine？</p>
 *
 * <pre>
 * 1. core 层已经通过 CacheProvider 做了抽象；
 * 2. provider 层就是具体技术实现层，需要明确当前实现依赖 Caffeine；
 * 3. 如果未来新增其他本地缓存实现，可以新增 XxxLocalCacheProvider，而不是改 core。
 * </pre>
 *
 * <p>当前实现没有直接使用 Caffeine 的 expireAfterWrite，而是使用 LocalCacheEntry.expireAtMillis 控制过期。</p>
 *
 * <p>这样做是为了支持：</p>
 *
 * <pre>
 * 1. 每个 key 独立 TTL；
 * 2. 正常值 TTL 与空值 TTL 不同；
 * 3. TTL 随机抖动；
 * 4. Redis 命中后回填 L1 时可指定单次写入 TTL。
 * </pre>
 */
public class CaffeineCacheProvider implements CacheProvider {

    /**
     * Caffeine Cache 管理器。
     *
     * <p>它负责按 cacheName 维护多个 Caffeine Cache 实例。</p>
     */
    private final CaffeineLocalCacheManager cacheManager;

    /**
     * 创建 Caffeine Provider。
     *
     * @param cacheManager Caffeine Cache 管理器
     */
    public CaffeineCacheProvider(CaffeineLocalCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * 返回 Provider 名称。
     *
     * @return provider 名称
     */
    @Override
    public String name() {
        return "caffeine";
    }

    /**
     * 查询本地缓存。
     *
     * <p>如果没有开启 L1，直接返回 miss。</p>
     * <p>如果本地缓存条目已经过期，则删除该条目并返回 miss。</p>
     * <p>如果命中空值占位，则返回 CacheValue.nullValue(L1)。</p>
     *
     * @param key 缓存 key
     * @param valueType 值类型，Caffeine 中对象未序列化，当前主要用于接口统一
     * @param spec 缓存策略
     * @return 缓存值状态
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> CacheValue<T> get(CacheKey key, Class<T> valueType, CacheSpec spec) {
        if (!spec.isEnableL1()) {
            return CacheValue.miss();
        }

        Cache<String, LocalCacheEntry> cache = cacheManager.getCache(key.cacheName(), spec);
        LocalCacheEntry entry = cache.getIfPresent(key.fullKey());

        if (entry == null) {
            return CacheValue.miss();
        }

        if (entry.isExpired()) {
            cache.invalidate(key.fullKey());
            return CacheValue.miss();
        }

        if (entry.isNullValue()) {
            return CacheValue.nullValue(CacheLevel.L1);
        }

        return CacheValue.of((T) entry.getValue(), CacheLevel.L1);
    }

    /**
     * 写入正常本地缓存。
     *
     * @param key 缓存 key
     * @param value 缓存值
     * @param ttl 本次写入 TTL
     * @param spec 缓存策略
     */
    @Override
    public void put(CacheKey key, Object value, Duration ttl, CacheSpec spec) {
        if (!spec.isEnableL1()) {
            return;
        }

        Cache<String, LocalCacheEntry> cache = cacheManager.getCache(key.cacheName(), spec);
        long expireAtMillis = System.currentTimeMillis() + ttl.toMillis();
        cache.put(key.fullKey(), LocalCacheEntry.of(value, expireAtMillis));
    }

    /**
     * 写入本地空值缓存。
     *
     * <p>空值缓存用于防穿透，TTL 通常要短于正常值 TTL。</p>
     *
     * @param key 缓存 key
     * @param ttl 空值缓存 TTL
     * @param spec 缓存策略
     */
    @Override
    public void putNullValue(CacheKey key, Duration ttl, CacheSpec spec) {
        if (!spec.isEnableL1()) {
            return;
        }

        Cache<String, LocalCacheEntry> cache = cacheManager.getCache(key.cacheName(), spec);
        long expireAtMillis = System.currentTimeMillis() + ttl.toMillis();
        cache.put(key.fullKey(), LocalCacheEntry.nullValue(expireAtMillis));
    }

    /**
     * 删除当前实例的本地缓存。
     *
     * <p>一期 evict 只影响当前 JVM 的 L1。多实例 L1 广播失效属于二期。</p>
     *
     * @param key 缓存 key
     */
    @Override
    public void evict(CacheKey key) {
        Cache<String, LocalCacheEntry> cache = cacheManager.getCache(key.cacheName(), CacheSpec.defaults(key.cacheName()));
        cache.invalidate(key.fullKey());
    }
}
