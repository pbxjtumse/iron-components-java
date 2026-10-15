package com.xjtu.iron.cache.api.enums;

/**
 * CacheNullPolicy 枚举。
 *
 * <p>用于描述缓存组件中的固定策略或状态，避免业务代码使用散乱字符串。</p>
 */
public enum CacheNullPolicy {

    /** 缓存空值，用于防止同一不存在数据被反复穿透查询。 */
    CACHE_NULL,

    /** 不把空值写入缓存。 */
    SKIP_NULL
}
