package com.xjtu.iron.foundation.core.collection;

import com.xjtu.iron.foundation.core.text.IronStrings;

import java.util.*;

/**
 * Iron Foundation Map 工具门面。
 */
public final class IronMaps {

    private IronMaps() {
    }

    /** 判断 Map 是否为 null 或空。 */
    public static boolean isEmpty(Map<?, ?> value) {
        return value == null || value.isEmpty();
    }

    /** 判断 Map 是否不为 null 且不为空。 */
    public static boolean isNotEmpty(Map<?, ?> value) {
        return !isEmpty(value);
    }

    /** null Map 转为空 Map。 */
    public static <K, V> Map<K, V> emptyIfNull(Map<K, V> value) {
        return value == null ? Collections.emptyMap() : value;
    }

    /** 创建不可变 Map 副本。 */
    public static <K, V> Map<K, V> immutableCopy(Map<K, V> value) {
        return value == null || value.isEmpty() ? Collections.emptyMap() : Map.copyOf(value);
    }

    /** 创建可变 LinkedHashMap 副本。 */
    public static <K, V> Map<K, V> mutableCopy(Map<K, V> value) {
        return value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
    }

    /** 获取字符串值；不存在时返回 null。 */
    public static String getString(Map<String, ?> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /** 获取字符串值；不存在或空白时返回默认值。 */
    public static String getString(Map<String, ?> map, String key, String defaultValue) {
        return IronStrings.defaultIfBlank(getString(map, key), defaultValue);
    }

    /** 获取 Integer；无法解析时返回 Optional.empty。 */
    public static Optional<Integer> getInteger(Map<String, ?> map, String key) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof Integer integer) {
            return Optional.of(integer);
        }
        if (value instanceof Number number) {
            return Optional.of(number.intValue());
        }
        try {
            return IronStrings.isBlank(String.valueOf(value)) ? Optional.empty() : Optional.of(Integer.parseInt(String.valueOf(value).trim()));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    /** 获取 Long；无法解析时返回 Optional.empty。 */
    public static Optional<Long> getLong(Map<String, ?> map, String key) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof Long longValue) {
            return Optional.of(longValue);
        }
        if (value instanceof Number number) {
            return Optional.of(number.longValue());
        }
        try {
            return IronStrings.isBlank(String.valueOf(value)) ? Optional.empty() : Optional.of(Long.parseLong(String.valueOf(value).trim()));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    /** 获取 Boolean；无法解析时返回 Optional.empty。 */
    public static Optional<Boolean> getBoolean(Map<String, ?> map, String key) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof Boolean booleanValue) {
            return Optional.of(booleanValue);
        }
        String text = IronStrings.trimToNull(value == null ? null : String.valueOf(value));
        if (text == null) {
            return Optional.empty();
        }
        if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
            return Optional.of(Boolean.parseBoolean(text));
        }
        return Optional.empty();
    }

    /** 当 key 和 value 均不为 null 时写入 Map。 */
    public static <K, V> void putIfNotNull(Map<K, V> map, K key, V value) {
        Objects.requireNonNull(map, "map must not be null");
        if (key != null && value != null) {
            map.put(key, value);
        }
    }

    /** 合并两个 Map；second 覆盖 first 中相同 Key 的值。 */
    public static <K, V> Map<K, V> merge(Map<K, V> first, Map<K, V> second) {
        Map<K, V> result = mutableCopy(first);
        if (second != null) {
            result.putAll(second);
        }
        return Collections.unmodifiableMap(result);
    }
}
