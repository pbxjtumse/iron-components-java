package com.xjtu.iron.cache.api.model;

import com.xjtu.iron.cache.api.enums.CacheConsistencyPolicy;
import com.xjtu.iron.cache.api.enums.CacheDegradePolicy;
import com.xjtu.iron.cache.api.enums.CacheNullPolicy;

import java.time.Duration;

/**
 * 缓存策略模型。
 *
 * <p>CacheSpec 描述一个 cacheName 的完整缓存行为。后续接入配置中心时，
 * 动态刷新的核心对象也是 CacheSpec。</p>
 */
public class CacheSpec {

    /** 缓存名称，用于和 CacheKey.cacheName 对应。 */
    private String cacheName;

    /** 是否启用 L1 本地缓存。true 表示会使用 Caffeine。 */
    private boolean enableL1 = true;

    /** 是否启用 L2 分布式缓存。true 表示会使用 Redis。 */
    private boolean enableL2 = true;

    /** 正常值 TTL。loader 返回非 null 数据时使用这个 TTL。 */
    private Duration ttl = Duration.ofMinutes(10);

    /** 空值 TTL。loader 返回 null 且 nullPolicy=CACHE_NULL 时使用这个 TTL。 */
    private Duration nullValueTtl = Duration.ofSeconds(30);

    /** TTL 随机抖动范围，用于避免大量 key 同时过期导致缓存雪崩。 */
    private Duration ttlJitter = Duration.ofSeconds(60);

    /** 是否开启互斥加载。一期使用本地锁防止单 JVM 内缓存击穿。 */
    private boolean mutexLoad = true;

    /** 空值策略。CACHE_NULL 表示缓存空值占位，SKIP_NULL 表示 null 不写缓存。 */
    private CacheNullPolicy nullPolicy = CacheNullPolicy.CACHE_NULL;

    /** 一致性策略。一期只是预留字段，二期会结合本地缓存失效通知使用。 */
    private CacheConsistencyPolicy consistencyPolicy = CacheConsistencyPolicy.EVENTUAL;

    /** 降级策略。Redis 等缓存层读取异常时，DefaultCacheClient 根据它决定如何处理。 */
    private CacheDegradePolicy degradePolicy = CacheDegradePolicy.LOAD_SOURCE;

    /** 创建一个带默认值的缓存策略。 */
    public static CacheSpec defaults(String cacheName) {
        CacheSpec spec = new CacheSpec();
        spec.setCacheName(cacheName);
        return spec;
    }

    /** 返回缓存名称。 */
    public String getCacheName() { return cacheName; }

    /** 设置缓存名称。 */
    public void setCacheName(String cacheName) { this.cacheName = cacheName; }

    /** 返回是否启用 L1。 */
    public boolean isEnableL1() { return enableL1; }

    /** 设置是否启用 L1。 */
    public void setEnableL1(boolean enableL1) { this.enableL1 = enableL1; }

    /** 返回是否启用 L2。 */
    public boolean isEnableL2() { return enableL2; }

    /** 设置是否启用 L2。 */
    public void setEnableL2(boolean enableL2) { this.enableL2 = enableL2; }

    /** 返回正常值 TTL。 */
    public Duration getTtl() { return ttl; }

    /** 设置正常值 TTL。 */
    public void setTtl(Duration ttl) { this.ttl = ttl; }

    /** 返回空值 TTL。 */
    public Duration getNullValueTtl() { return nullValueTtl; }

    /** 设置空值 TTL。 */
    public void setNullValueTtl(Duration nullValueTtl) { this.nullValueTtl = nullValueTtl; }

    /** 返回 TTL 抖动范围。 */
    public Duration getTtlJitter() { return ttlJitter; }

    /** 设置 TTL 抖动范围。 */
    public void setTtlJitter(Duration ttlJitter) { this.ttlJitter = ttlJitter; }

    /** 返回是否开启互斥加载。 */
    public boolean isMutexLoad() { return mutexLoad; }

    /** 设置是否开启互斥加载。 */
    public void setMutexLoad(boolean mutexLoad) { this.mutexLoad = mutexLoad; }

    /** 返回空值缓存策略。 */
    public CacheNullPolicy getNullPolicy() { return nullPolicy; }

    /** 设置空值缓存策略。 */
    public void setNullPolicy(CacheNullPolicy nullPolicy) { this.nullPolicy = nullPolicy; }

    /** 返回一致性策略。 */
    public CacheConsistencyPolicy getConsistencyPolicy() { return consistencyPolicy; }

    /** 设置一致性策略。 */
    public void setConsistencyPolicy(CacheConsistencyPolicy consistencyPolicy) { this.consistencyPolicy = consistencyPolicy; }

    /** 返回缓存异常降级策略。 */
    public CacheDegradePolicy getDegradePolicy() { return degradePolicy; }

    /** 设置缓存异常降级策略。 */
    public void setDegradePolicy(CacheDegradePolicy degradePolicy) { this.degradePolicy = degradePolicy; }
}
