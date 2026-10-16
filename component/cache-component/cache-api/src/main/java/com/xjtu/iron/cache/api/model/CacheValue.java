package com.xjtu.iron.cache.api.model;

import com.xjtu.iron.cache.api.enums.CacheLevel;

/**
 * Provider 层返回的缓存值。
 *
 * <p>这里没有直接使用 Optional，因为缓存读取结果有三种状态：</p>
 * <pre>
 * 1. 命中正常值
 * 2. 命中空值占位
 * 3. 未命中
 * </pre>
 *
 * <p>Optional 只能表达“有值/没值”，无法区分“未命中”和“命中空值占位”。</p>
 */
public class CacheValue<T> {

    /** 是否命中缓存。true 表示命中正常值或命中空值占位。 */
    private final boolean present;

    /** 是否为空值占位。true 表示该 key 命中了缓存中的 null 标记。 */
    private final boolean nullValue;

    /** 缓存中的真实值。命中空值占位或未命中时为 null。 */
    private final T value;

    /** 命中的缓存层级，例如 L1、L2。未命中时为 NONE。 */
    private final CacheLevel level;

    /** 私有构造方法，外部通过静态工厂方法创建。 */
    private CacheValue(boolean present, boolean nullValue, T value, CacheLevel level) {
        this.present = present;
        this.nullValue = nullValue;
        this.value = value;
        this.level = level;
    }

    /** 构造未命中结果。 */
    public static <T> CacheValue<T> miss() {
        return new CacheValue<>(false, false, null, CacheLevel.NONE);
    }

    /** 构造命中正常值结果。 */
    public static <T> CacheValue<T> of(T value, CacheLevel level) {
        return new CacheValue<>(true, false, value, level);
    }

    /** 构造命中空值占位结果。 */
    public static <T> CacheValue<T> nullValue(CacheLevel level) {
        return new CacheValue<>(true, true, null, level);
    }

    /** 返回是否命中缓存。 */
    public boolean isPresent() { return present; }

    /** 返回是否命中空值占位。 */
    public boolean isNullValue() { return nullValue; }

    /** 返回缓存真实值。 */
    public T getValue() { return value; }

    /** 返回命中层级。 */
    public CacheLevel getLevel() { return level; }
}
