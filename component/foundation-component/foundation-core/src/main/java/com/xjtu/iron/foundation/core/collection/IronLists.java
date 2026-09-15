package com.xjtu.iron.foundation.core.collection;

import java.util.*;
import java.util.function.Function;

/**
 * Iron Foundation List 工具门面。
 *
 * <p>该类只承接 List 上最常见的安全访问、分片、不可变副本和排序副本能力。</p>
 */
public final class IronLists {

    private IronLists() {
    }

    /** null List 转为空 List。 */
    public static <T> List<T> emptyIfNull(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }

    /** 创建不可变 List 副本。 */
    public static <T> List<T> immutableCopy(List<T> values) {
        return values == null || values.isEmpty() ? Collections.emptyList() : List.copyOf(values);
    }

    /** 创建可变 ArrayList 副本。 */
    public static <T> List<T> mutableCopy(List<T> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    /** 返回第一个元素。 */
    public static <T> Optional<T> first(List<T> values) {
        return values == null || values.isEmpty() ? Optional.empty() : Optional.ofNullable(values.get(0));
    }

    /** 返回最后一个元素。 */
    public static <T> Optional<T> last(List<T> values) {
        return values == null || values.isEmpty() ? Optional.empty() : Optional.ofNullable(values.get(values.size() - 1));
    }

    /** 安全获取指定下标元素。 */
    public static <T> Optional<T> get(List<T> values, int index) {
        if (values == null || index < 0 || index >= values.size()) {
            return Optional.empty();
        }
        return Optional.ofNullable(values.get(index));
    }

    /** 将 List 按指定大小拆分成不可变分片。 */
    public static <T> List<List<T>> partition(List<T> values, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<List<T>> partitions = new ArrayList<>();
        for (List<T> part : org.apache.commons.collections4.ListUtils.partition(values, size)) {
            partitions.add(List.copyOf(part));
        }
        return Collections.unmodifiableList(partitions);
    }

    /** 返回反转后的不可变副本，不修改原 List。 */
    public static <T> List<T> reversedCopy(List<T> values) {
        List<T> copy = mutableCopy(values);
        Collections.reverse(copy);
        return Collections.unmodifiableList(copy);
    }

    /** 返回排序后的不可变副本，不修改原 List。 */
    public static <T> List<T> sortedCopy(List<T> values, Comparator<? super T> comparator) {
        Objects.requireNonNull(comparator, "comparator must not be null");
        List<T> copy = mutableCopy(values);
        copy.sort(comparator);
        return Collections.unmodifiableList(copy);
    }

    /** 拼接两个 List，并返回不可变副本。 */
    public static <T> List<T> concat(List<T> first, List<T> second) {
        if ((first == null || first.isEmpty()) && (second == null || second.isEmpty())) {
            return Collections.emptyList();
        }
        List<T> result = new ArrayList<>();
        if (first != null) {
            result.addAll(first);
        }
        if (second != null) {
            result.addAll(second);
        }
        return Collections.unmodifiableList(result);
    }

    /** 按 Key 保序去重，遇到重复 Key 时保留第一个元素。 */
    public static <T, K> List<T> distinctBy(List<T> values, Function<? super T, ? extends K> keyMapper) {
        return IronCollections.distinctBy(values, keyMapper);
    }
}
