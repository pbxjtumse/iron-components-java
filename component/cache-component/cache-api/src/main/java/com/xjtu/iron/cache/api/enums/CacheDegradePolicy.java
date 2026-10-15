package com.xjtu.iron.cache.api.enums;

/**
 * CacheDegradePolicy 枚举。
 *
 * <p>用于描述缓存组件中的固定策略或状态，避免业务代码使用散乱字符串。</p>
 */
public enum CacheDegradePolicy {

    /** 缓存不可用时回源加载真实数据。 */
    LOAD_SOURCE,

    /** 缓存不可用时直接返回空值。 */
    RETURN_NULL,

    /** 缓存不可用时立即抛出异常。 */
    FAIL_FAST
}
