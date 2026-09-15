package com.xjtu.iron.cache.core.impl;

import com.xjtu.iron.cache.api.enums.CacheLevel;
import com.xjtu.iron.cache.api.enums.CacheOperation;
import com.xjtu.iron.cache.api.key.CacheKey;
import com.xjtu.iron.cache.core.CacheMetricsRecorder;

/**
 * 空指标记录器。
 *
 * <p>当业务系统没有引入 MeterRegistry 时使用，保证缓存组件不会因为缺少观测系统而启动失败。</p>
 */
public class NoopCacheMetricsRecorder implements CacheMetricsRecorder {

    @Override
    public void recordHit(CacheKey key, CacheLevel level, long costMillis) {
    }

    @Override
    public void recordMiss(CacheKey key, long costMillis) {
    }

    @Override
    public void recordLoad(CacheKey key, long costMillis) {
    }

    @Override
    public void recordError(CacheKey key, CacheOperation operation, Throwable throwable) {
    }
}
