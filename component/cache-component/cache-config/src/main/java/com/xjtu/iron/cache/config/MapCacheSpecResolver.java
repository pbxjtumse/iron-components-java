package com.xjtu.iron.cache.config;

import com.xjtu.iron.cache.api.key.CacheKey;
import com.xjtu.iron.cache.api.model.CacheSpec;
import com.xjtu.iron.cache.core.CacheSpecResolver;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存 Map 的缓存策略解析器。
 *
 * <p>它适合非 Spring 环境、单元测试或后续接入配置中心后的本地策略缓存。</p>
 */
public class MapCacheSpecResolver implements CacheSpecResolver {

    /** cacheName 到 CacheSpec 的映射。 */
    private final Map<String, CacheSpec> specMap = new ConcurrentHashMap<>();

    /** 创建空的策略解析器。 */
    public MapCacheSpecResolver() { }

    /** 使用指定策略集合创建解析器。 */
    public MapCacheSpecResolver(Map<String, CacheSpec> specs) {
        if (specs != null) {
            this.specMap.putAll(specs);
        }
    }

    /** 根据 CacheKey 解析策略。 */
    @Override
    public CacheSpec resolve(CacheKey key) {
        return resolve(key.cacheName());
    }

    /** 根据 cacheName 解析策略；没有配置时返回默认策略。 */
    @Override
    public CacheSpec resolve(String cacheName) {
        return specMap.getOrDefault(cacheName, CacheSpec.defaults(cacheName));
    }

    /** 新增或覆盖指定 cacheName 的策略。 */
    public void put(String cacheName, CacheSpec spec) {
        specMap.put(cacheName, spec);
    }

    /** 删除指定 cacheName 的策略。 */
    public void remove(String cacheName) {
        specMap.remove(cacheName);
    }
}
