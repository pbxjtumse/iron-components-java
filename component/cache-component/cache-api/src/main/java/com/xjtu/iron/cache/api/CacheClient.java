package com.xjtu.iron.cache.api;

import com.xjtu.iron.cache.api.key.CacheKey;
import com.xjtu.iron.cache.api.loader.CacheLoader;
import com.xjtu.iron.cache.api.model.CacheResult;
import com.xjtu.iron.cache.api.model.CacheSpec;

import java.time.Duration;
import java.util.Optional;

/**
 * 统一缓存访问入口。
 *
 * <p>业务系统应该优先通过 CacheClient 访问普通业务缓存，
 * 而不是直接使用 RedisTemplate、StringRedisTemplate、Caffeine Cache 或 RedissonClient。</p>
 *
 * <p>CacheClient 的职责是屏蔽底层缓存实现，并统一处理：</p>
 *
 * <pre>
 * 1. key 规范；
 * 2. TTL 策略；
 * 3. L1 / L2 多级缓存访问；
 * 4. 空值缓存；
 * 5. 缓存击穿保护；
 * 6. 缓存异常降级；
 * 7. 缓存指标；
 * 8. 二期本地缓存失效事件。
 * </pre>
 *
 * <p>一期默认实现：</p>
 *
 * <pre>
 * DefaultCacheClient
 *   -> CompositeCacheProvider
 *      -> CaffeineCacheProvider
 *      -> RedisCacheProvider
 * </pre>
 */
public interface CacheClient {

    /**
     * 按 cacheName 默认策略读取缓存。
     *
     * <pre>
     * 1. 根据 key.cacheName() 解析 CacheSpec；
     * 2. 查 L1 Caffeine；
     * 3. L1 miss 后查 L2 Redis；
     * 4. L2 miss 后执行 loader；
     * 5. loader 返回后写入 L2 和 L1；
     * 6. 返回 CacheResult。
     * </pre>
     *
     * @param key 缓存 key，包含 cacheName、namespace、bizKey、version
     * @param valueType 缓存值类型，用于 Redis 反序列化
     * @param loader 缓存未命中时的数据加载逻辑，通常是数据库查询、RPC 查询或外部接口查询
     * @return 缓存访问结果，包含 value、命中层级、命中状态、耗时等诊断信息
     */
    <T> CacheResult<T> get(CacheKey key, Class<T> valueType, CacheLoader<T> loader);

    /**
     * 按调用方显式传入的缓存策略读取缓存。
     *
     * <p>这个方法用于少数需要临时覆盖默认策略的场景。普通业务更推荐使用
     * {@link #get(CacheKey, Class, CacheLoader)}，让策略统一来自配置。</p>
     *
     * @param key 缓存 key
     * @param valueType 缓存值类型
     * @param spec 本次调用使用的缓存策略
     * @param loader 缓存未命中时的数据加载逻辑
     * @return 缓存访问结果
     */
    <T> CacheResult<T> get(CacheKey key, Class<T> valueType, CacheSpec spec, CacheLoader<T> loader);

    /**
     * 只读取缓存，不执行 loader。
     *
     * <pre>
     * 1. 只想探测缓存是否存在；
     * 2. 不希望触发源数据加载；
     * 3. 降级逻辑中只尝试读缓存。
     * </pre>
     *
     * @param key 缓存 key
     * @param valueType 缓存值类型
     * @return 当前缓存中的真实值；不会触发源数据加载
     */
    <T> Optional<T> getIfPresent(CacheKey key, Class<T> valueType);

    /**
     * 写入缓存。
     *
     * <p>TTL 使用 key.cacheName() 对应的 CacheSpec。</p>
     *
     * <p>如果 value 为 null：</p>
     *
     * <pre>
     * 1. nullPolicy = CACHE_NULL，则写入空值缓存；
     * 2. nullPolicy = SKIP_NULL，则不写缓存。
     * </pre>
     */
    void put(CacheKey key, Object value);

    /**
     * 使用指定 TTL 写入缓存。
     *
     * <p>适合个别业务需要临时指定 TTL 的场景。</p>
     *
     * <p>注意：</p>
     *
     * <pre>
     * 这个方法不会使用 CacheSpec 里的 ttl 和 ttlJitter；
     * 但仍然会使用 CacheSpec 判断是否开启 L1 / L2，以及 nullPolicy。
     *
     * @param key 缓存 key
     * @param value 要写入的缓存值
     * @param ttl 本次写入使用的过期时间
     */
    void put(CacheKey key, Object value, Duration ttl);

    /**
     * 使用指定 CacheSpec 写入缓存。
     *
     * <p>这个方法需要和 DefaultCacheClient 保持一致。</p>
     *
     * <p>如果不希望业务直接传 CacheSpec，可以删除这个方法，
     * 同时也要删除 DefaultCacheClient 中对应实现。</p>
     *
     * <p>我建议保留，因为它对测试、特殊缓存策略和后续扩展比较有用。</p>
     *
     * @param key 缓存 key
     * @param value 要写入的缓存值
     * @param spec 本次写入使用的缓存策略
     */
    void put(CacheKey key, Object value, CacheSpec spec);

    /**
     * 删除缓存。
     *
     * <p>一期行为：</p>
     *
     * <pre>
     * 1. 删除当前实例 L1 Caffeine；
     * 2. 删除 L2 Redis。
     * </pre>
     *
     * <p>二期行为：</p>
     *
     * <pre>
     * 1. 删除当前实例 L1 Caffeine；
     * 2. 删除 L2 Redis；
     * 3. 发布缓存失效事件；
     * 4. 其他实例收到事件后删除自己的 L1。
     * </pre>
     */
    void evict(CacheKey key);

    /**
     * 刷新缓存。
     *
     * <p>会执行 loader，然后将 loader 返回值写入缓存。</p>
     *
     * <p>适合：</p>
     *
     * <pre>
     * 1. 缓存预热；
     * 2. 手动刷新；
     * 3. 后台任务刷新；
     * 4. 配置类缓存刷新。
     * </pre>
     *
     * @param key 缓存 key
     * @param valueType 缓存值类型，当前一期主要用于语义表达
     * @param loader 最新数据加载逻辑
     */
    void refresh(CacheKey key, Class<?> valueType, CacheLoader<?> loader);

    void refresh(CacheKey key, Class<?> valueType, CacheSpec spec, CacheLoader<?> loader);



}
