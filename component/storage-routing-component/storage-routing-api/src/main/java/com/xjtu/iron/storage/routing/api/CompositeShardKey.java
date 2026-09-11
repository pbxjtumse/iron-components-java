package com.xjtu.iron.storage.routing.api;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 有序、非空、字段名不重复的不可变分片键集合。单字段同样使用这个模型，无需另建输入类型。
 *
 * <p>调用方应按固定规则顺序提供字段，例如始终是 tenant_id、order_id；不依赖 Map 遍历顺序。
 * 字段顺序属于键身份的一部分，本类不会自动排序。字段名区分大小写，去除首尾空格后检查重名。</p>
 */
public final class CompositeShardKey {

    private final List<ShardKey> keys;

    private CompositeShardKey(List<ShardKey> keys) {
        Objects.requireNonNull(keys, "shard keys must not be null");
        if (keys.isEmpty()) {
            throw new StorageRoutingException("CompositeShardKey must contain at least one field");
        }
        this.keys = List.copyOf(keys);
        Set<String> names = new HashSet<>();
        for (ShardKey key : this.keys) {
            if (!names.add(key.name())) {
                throw new StorageRoutingException("Duplicate shard key name: " + key.name());
            }
        }
    }

    public static CompositeShardKey of(ShardKey... keys) {
        return from(Arrays.asList(Objects.requireNonNull(keys, "shard keys must not be null")));
    }

    public static CompositeShardKey from(List<ShardKey> keys) {
        return new CompositeShardKey(keys);
    }

    public List<ShardKey> keys() {
        return keys;
    }

    public int size() {
        return keys.size();
    }

    /** 旧单字段读取入口使用此方法；复合键不能静默截取第一个字段。 */
    public ShardKey singleKey() {
        if (keys.size() != 1) {
            throw new StorageRoutingException("Expected a single shard key; read all fields through context().shardKey()");
        }
        return keys.get(0);
    }

    /**
     * 复合键 v1 编码：版本、字段数以及按顺序排列的字段名、类型名、值文本。
     *
     * <p>每个字符串均使用“UTF-16 code unit 长度:文本”编码，与 Java String.length/hashCode 一致。
     * 长度前缀保护字段边界，因此 ab+c 与 a+bc、包含冒号的值、不同类型的同名文本都不会被误编码为同一个键。
     * 编码只保证无拼接歧义，最终有限分片数下仍会有正常的哈希碰撞。</p>
     */
    public String canonicalForm() {
        StringBuilder result = new StringBuilder("v1;").append(keys.size()).append(';');
        for (ShardKey key : keys) {
            appendPart(result, key.name());
            appendPart(result, key.value().type().name());
            appendPart(result, key.value().canonicalText());
        }
        return result.toString();
    }

    private static void appendPart(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof CompositeShardKey other && keys.equals(other.keys);
    }

    @Override
    public int hashCode() {
        return keys.hashCode();
    }

    @Override
    public String toString() {
        return "CompositeShardKey{keys=" + keys + '}';
    }
}
