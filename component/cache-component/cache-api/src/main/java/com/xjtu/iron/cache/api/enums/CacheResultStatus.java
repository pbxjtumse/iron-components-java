package com.xjtu.iron.cache.api.enums;

/**
 * CacheResultStatus 枚举。
 *
 * <p>用于描述缓存组件中的固定策略或状态，避免业务代码使用散乱字符串。</p>
 */
public enum CacheResultStatus {

    /** 命中缓存并直接取得结果。 */
    HIT,

    /** 缓存未命中，已通过 loader 回源加载结果。 */
    MISS_LOADED,

    /** 缓存与回源均未得到有效值。 */
    EMPTY,

    /** 缓存处理失败，且未成功降级。 */
    ERROR,

    /** 缓存异常后已按降级策略返回结果。 */
    DEGRADED
}
