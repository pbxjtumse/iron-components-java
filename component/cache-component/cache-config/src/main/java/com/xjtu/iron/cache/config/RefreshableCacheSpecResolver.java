package com.xjtu.iron.cache.config;


import com.xjtu.iron.cache.api.key.CacheKey;
import com.xjtu.iron.cache.api.model.CacheSpec;
import com.xjtu.iron.cache.core.CacheSpecResolver;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 可刷新缓存策略解析器。
 *
 * <p>二期动态配置的基础能力。</p>
 *
 * <p>它不直接依赖 Nacos / Apollo。</p>
 *
 * <p>后续不管从哪里加载配置：</p>
 *
 * <pre>
 * 1. 本地配置文件；
 * 2. Nacos；
 * 3. Apollo；
 * 4. 数据库；
 * 5. 管理后台；
 * </pre>
 *
 * <p>最终都可以调用 reload(Map) 刷新缓存策略。</p>
 */
public class RefreshableCacheSpecResolver implements CacheSpecResolver {

    private final AtomicReference<Map<String, CacheSpec>> specsRef =
            new AtomicReference<>(Collections.emptyMap());

    public RefreshableCacheSpecResolver() {
    }

    public RefreshableCacheSpecResolver(Map<String, CacheSpec> initialSpecs) {
        reload(initialSpecs);
    }

    @Override
    public CacheSpec resolve(CacheKey key) {
        return resolve(key.cacheName());
    }

    @Override
    public CacheSpec resolve(String cacheName) {
        CacheSpec spec = specsRef.get().get(cacheName);

        if (spec == null) {
            return CacheSpec.defaults(cacheName);
        }

        return spec;
    }

    /**
     * 全量刷新缓存策略。
     *
     * <p>使用 copy-on-write 方式替换整份配置，避免并发读写问题。</p>
     */
    public void reload(Map<String, CacheSpec> newSpecs) {
        if (newSpecs == null || newSpecs.isEmpty()) {
            specsRef.set(Collections.emptyMap());
            return;
        }

        specsRef.set(Collections.unmodifiableMap(new HashMap<>(newSpecs)));
    }

    /**
     * 刷新单个 cacheName 的缓存策略。
     */
    public void refreshOne(String cacheName, CacheSpec spec) {
        Map<String, CacheSpec> oldMap = specsRef.get();
        Map<String, CacheSpec> newMap = new HashMap<>(oldMap);

        if (spec == null) {
            newMap.remove(cacheName);
        } else {
            newMap.put(cacheName, spec);
        }

        specsRef.set(Collections.unmodifiableMap(newMap));
    }

    public Map<String, CacheSpec> snapshot() {
        return specsRef.get();
    }
}
