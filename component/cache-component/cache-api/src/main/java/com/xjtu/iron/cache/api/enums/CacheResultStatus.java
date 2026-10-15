package com.xjtu.iron.cache.api.enums;

/**
 * CacheResultStatus 枚举。
 *
 * <p>用于描述缓存组件中的固定策略或状态，避免业务代码使用散乱字符串。</p>
 */
public enum CacheResultStatus {
    HIT,
    MISS_LOADED,
    EMPTY,
    ERROR,
    DEGRADED
}
