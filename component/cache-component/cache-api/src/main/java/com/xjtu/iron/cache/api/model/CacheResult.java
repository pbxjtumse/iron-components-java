package com.xjtu.iron.cache.api.model;

import com.xjtu.iron.cache.api.enums.CacheLevel;
import com.xjtu.iron.cache.api.enums.CacheResultStatus;

/**
 * CacheClient 返回给业务方的缓存访问结果。
 *
 * <p>相比直接返回 value，CacheResult 额外保留了命中状态、命中层级、耗时等信息。
 * 业务如果只关心值，可以直接调用 {@link #getValue()}。</p>
 */
public class CacheResult<T> {

    /** 业务真实值。命中空值占位或 loader 返回 null 时可能为 null。 */
    private final T value;

    /** 本次缓存访问状态，例如 HIT、MISS_LOADED、DEGRADED。 */
    private final CacheResultStatus status;

    /** 命中层级。L1 表示本地缓存，L2 表示 Redis，SOURCE 表示源数据加载。 */
    private final CacheLevel level;

    /** 是否命中缓存。注意：从源数据加载成功不算 hit。 */
    private final boolean hit;

    /** 是否为空值结果。命中空值占位或 loader 返回 null 时为 true。 */
    private final boolean nullValue;

    /** 本次访问耗时，单位毫秒。 */
    private final long costMillis;

    /** 私有构造方法，外部通过静态工厂方法创建。 */
    private CacheResult(T value, CacheResultStatus status, CacheLevel level, boolean hit, boolean nullValue, long costMillis) {
        this.value = value;
        this.status = status;
        this.level = level;
        this.hit = hit;
        this.nullValue = nullValue;
        this.costMillis = costMillis;
    }

    /** 根据 Provider 命中的 CacheValue 构造结果。 */
    public static <T> CacheResult<T> hit(CacheValue<T> cacheValue, long costMillis) {
        return new CacheResult<>(cacheValue.getValue(), CacheResultStatus.HIT, cacheValue.getLevel(), true, cacheValue.isNullValue(), costMillis);
    }

    /** 构造 miss 后从源数据加载成功的结果。 */
    public static <T> CacheResult<T> loaded(T value, long costMillis) {
        return new CacheResult<>(value, CacheResultStatus.MISS_LOADED, CacheLevel.SOURCE, false, value == null, costMillis);
    }

    /** 构造缓存异常后走降级策略得到的结果。 */
    public static <T> CacheResult<T> degraded(T value, long costMillis) {
        return new CacheResult<>(value, CacheResultStatus.DEGRADED, CacheLevel.NONE, false, value == null, costMillis);
    }

    /** 返回业务值。 */
    public T getValue() { return value; }

    /** 返回访问状态。 */
    public CacheResultStatus getStatus() { return status; }

    /** 返回命中层级。 */
    public CacheLevel getLevel() { return level; }

    /** 返回是否命中缓存。 */
    public boolean isHit() { return hit; }

    /** 返回是否为空值结果。 */
    public boolean isNullValue() { return nullValue; }

    /** 返回耗时毫秒数。 */
    public long getCostMillis() { return costMillis; }
}
