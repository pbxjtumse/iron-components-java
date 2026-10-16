package com.xjtu.iron.foundation.core.collection;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Iron Foundation 集合工具门面。
 *
 * <p>该类对 JDK Stream 与 Apache Commons Collections 的高频能力做薄封装，统一空集合、不可变副本、分组、
 * 分片和差异计算等语义。命名为 {@code IronCollections} 是为了避免与
 * {@code java.util.Collections} 和 {@code org.apache.commons.collections4.CollectionUtils} 产生导入冲突。</p>
 */
public final class IronCollections {

    private IronCollections() {
    }

    /** 判断集合是否为 null 或空。 */
    public static boolean isEmpty(Collection<?> values) {
        return org.apache.commons.collections4.CollectionUtils.isEmpty(values);
    }

    /** 判断集合是否不为 null 且不为空。 */
    public static boolean isNotEmpty(Collection<?> values) {
        return org.apache.commons.collections4.CollectionUtils.isNotEmpty(values);
    }

    /** null 集合转换为空集合；非 null 集合原样返回。 */
    public static <T> Collection<T> emptyIfNull(Collection<T> values) {
        return values == null ? Collections.emptyList() : values;
    }

    /** 创建不可变 List 副本；null 或空集合返回空 List。 */
    public static <T> List<T> immutableCopy(Collection<T> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return List.copyOf(values);
    }

    /** 创建可变 List 副本；null 集合返回空 ArrayList。 */
    public static <T> List<T> mutableCopy(Collection<T> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    /** 返回集合第一个元素；集合为空时返回 Optional.empty。 */
    public static <T> Optional<T> first(Collection<T> values) {
        if (values == null || values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(values.iterator().next());
    }

    /** 过滤 null 元素，并返回不可变 List。 */
    public static <T> List<T> filterNotNull(Collection<T> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return values.stream().filter(Objects::nonNull).collect(Collectors.toUnmodifiableList());
    }

    /** 对集合元素进行转换，并返回不可变 List。 */
    public static <T, R> List<R> map(Collection<T> values, Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return values.stream().map(mapper).collect(Collectors.toUnmodifiableList());
    }

    /** 对集合元素进行过滤，并返回不可变 List。 */
    public static <T> List<T> filter(Collection<T> values, Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "predicate must not be null");
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return values.stream().filter(predicate).collect(Collectors.toUnmodifiableList());
    }

    /** 将集合转为 Map，遇到重复 Key 时保留第一次出现的元素。 */
    public static <T, K> Map<K, T> toMap(Collection<T> values, Function<? super T, ? extends K> keyMapper) {
        Objects.requireNonNull(keyMapper, "keyMapper must not be null");
        if (values == null || values.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<K, T> result = new LinkedHashMap<>();
        for (T value : values) {
            if (value == null) {
                continue;
            }
            K key = keyMapper.apply(value);
            if (key != null && !result.containsKey(key)) {
                result.put(key, value);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /** 按 Key 分组，并保证外层 Map 和内层 List 均不可修改。 */
    public static <T, K> Map<K, List<T>> groupBy(Collection<T> values, Function<? super T, ? extends K> keyMapper) {
        Objects.requireNonNull(keyMapper, "keyMapper must not be null");
        if (values == null || values.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<K, List<T>> grouped = values.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(keyMapper, LinkedHashMap::new, Collectors.toList()));
        Map<K, List<T>> result = new LinkedHashMap<>();
        grouped.forEach((key, list) -> result.put(key, List.copyOf(list)));
        return Collections.unmodifiableMap(result);
    }

    /** 按 Key 保序去重，遇到重复 Key 时保留第一个元素。 */
    public static <T, K> List<T> distinctBy(Collection<T> values, Function<? super T, ? extends K> keyMapper) {
        Objects.requireNonNull(keyMapper, "keyMapper must not be null");
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        Set<K> seen = new LinkedHashSet<>();
        List<T> result = new ArrayList<>();
        for (T value : values) {
            if (value == null) {
                continue;
            }
            K key = keyMapper.apply(value);
            if (seen.add(key)) {
                result.add(value);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** 判断两个集合是否存在交集。 */
    public static boolean containsAny(Collection<?> left, Collection<?> right) {
        return org.apache.commons.collections4.CollectionUtils.containsAny(left, right);
    }

    /** 返回两个集合的交集副本，结果不可修改。 */
    public static <T> List<T> intersection(Collection<T> left, Collection<T> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return Collections.emptyList();
        }
        return List.copyOf(org.apache.commons.collections4.CollectionUtils.intersection(left, right));
    }

    /** 返回两个集合的并集副本，结果不可修改。 */
    public static <T> List<T> union(Collection<T> left, Collection<T> right) {
        Collection<T> union = org.apache.commons.collections4.CollectionUtils.union(emptyIfNull(left), emptyIfNull(right));
        return union.isEmpty() ? Collections.emptyList() : List.copyOf(union);
    }

    /** 返回 left 减去 right 后的集合副本，结果不可修改。 */
    public static <T> List<T> subtract(Collection<T> left, Collection<T> right) {
        if (left == null || left.isEmpty()) {
            return Collections.emptyList();
        }
        Collection<T> subtract = org.apache.commons.collections4.CollectionUtils.subtract(left, emptyIfNull(right));
        return subtract.isEmpty() ? Collections.emptyList() : List.copyOf(subtract);
    }

    /** 按 Key 计算新旧集合差异。 */
    public static <T, K> CollectionDiff<T> difference(Collection<T> oldValues,
                                                      Collection<T> newValues,
                                                      Function<? super T, ? extends K> keyMapper) {
        Objects.requireNonNull(keyMapper, "keyMapper must not be null");
        Map<K, T> oldMap = toMap(emptyIfNull(oldValues), keyMapper);
        Map<K, T> newMap = toMap(emptyIfNull(newValues), keyMapper);
        List<T> added = new ArrayList<>();
        List<T> removed = new ArrayList<>();
        List<T> retained = new ArrayList<>();
        for (Map.Entry<K, T> entry : newMap.entrySet()) {
            if (oldMap.containsKey(entry.getKey())) {
                retained.add(entry.getValue());
            } else {
                added.add(entry.getValue());
            }
        }
        for (Map.Entry<K, T> entry : oldMap.entrySet()) {
            if (!newMap.containsKey(entry.getKey())) {
                removed.add(entry.getValue());
            }
        }
        return new CollectionDiff<>(added, removed, retained);
    }
}
