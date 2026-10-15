package com.xjtu.iron.cache.api.enums;

/**
 * CacheDegradePolicy 枚举。
 *
 * <p>用于描述缓存组件中的固定策略或状态，避免业务代码使用散乱字符串。</p>
 */
public enum CacheDegradePolicy {
    LOAD_SOURCE,
    RETURN_NULL,
    FAIL_FAST
}
