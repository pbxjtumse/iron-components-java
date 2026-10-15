package com.xjtu.iron.cache.api.enums;

/**
 * CacheOperation 枚举。
 *
 * <p>用于描述缓存组件中的固定策略或状态，避免业务代码使用散乱字符串。</p>
 */
public enum CacheOperation {

    /** 读取缓存。 */
    GET,

    /** 写入或更新缓存。 */
    PUT,

    /** 删除指定缓存数据。 */
    EVICT,

    /** 重新加载并刷新缓存。 */
    REFRESH,
    /**
     * 发布缓存事件。
     */
    PUBLISH_EVENT
}
