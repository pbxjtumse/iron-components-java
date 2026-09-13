package com.xjtu.iron.cache.core;

import com.xjtu.iron.cache.api.enums.CacheLevel;
import com.xjtu.iron.cache.api.enums.CacheOperation;
import com.xjtu.iron.cache.api.key.CacheKey;

/**
 * 缓存指标记录器。
 *
 * <p>core 只定义接口，不绑定 Micrometer。observability 模块提供 Micrometer 实现。</p>
 */
public interface CacheMetricsRecorder {

    /** 记录缓存命中。 */
    void recordHit(CacheKey key, CacheLevel level, long costMillis);

    /** 记录缓存未命中。 */
    void recordMiss(CacheKey key, long costMillis);

    /** 记录 loader 加载。 */
    void recordLoad(CacheKey key, long costMillis);

    /** 记录缓存操作异常。 */
    void recordError(CacheKey key, CacheOperation operation, Throwable throwable);
}
