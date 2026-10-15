package com.xjtu.iron.cache.api.enums;

/**
 * CacheConsistencyPolicy 枚举。
 *
 * <p>用于描述缓存组件中的固定策略或状态，避免业务代码使用散乱字符串。</p>
 */
public enum CacheConsistencyPolicy {
    WEAK,
    EVENTUAL,
    NEAR_STRONG,
    STRONG
}
