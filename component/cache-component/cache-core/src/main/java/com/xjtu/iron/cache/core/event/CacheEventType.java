package com.xjtu.iron.cache.core.event;




/**
 * 缓存事件类型。
 *
 * <p>二期第一版只真正使用 EVICT_KEY。</p>
 *
 * <p>其他类型先预留，后面可以用于：</p>
 *
 * <pre>
 * CLEAR_CACHE_NAME：清理整个 cacheName
 * REFRESH_KEY：刷新某个 key
 * SPEC_CHANGED：缓存策略发生变化
 * </pre>
 */
public enum CacheEventType {

    /**
     * 未知事件类型。
     *
     * <p>用于兼容未来新增的事件类型。</p>
     *
     * <p>当旧版本应用收到新版本应用发布的未知事件类型时，
     * 可以反序列化成 UNKNOWN，并选择忽略。</p>
     */
    UNKNOWN,

    /**
     * 删除单个缓存 key。
     *
     * <p>典型场景：</p>
     *
     * <pre>
     * 某个实例调用 cacheClient.evict(key)
     * 删除 Redis 和当前实例 L1 后
     * 发布 EVICT_KEY 事件
     * 其他实例收到后删除自己的 L1
     * </pre>
     */
    EVICT_KEY,

    /**
     * 清理某个 cacheName 下的本地缓存。
     *
     * <p>二期第一版先不实现。</p>
     */
    CLEAR_CACHE_NAME,

    /**
     * 刷新某个缓存 key。
     *
     * <p>二期第一版先不实现。</p>
     */
    REFRESH_KEY,

    /**
     * 缓存策略变更。
     *
     * <p>后续动态配置中心接入时使用。</p>
     */
    SPEC_CHANGED
}