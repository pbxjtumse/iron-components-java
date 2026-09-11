package com.xjtu.iron.storage.routing.api;

import java.util.Objects;

/** 一个分片字段：字段名 + 带类型的值，例如 tenant_id + LONG(1001)。 */
public final class ShardKey {

    private final String name;
    private final ShardValue value;

    public ShardKey(String name, ShardValue value) {
        if (name == null || name.isBlank()) {
            throw new StorageRoutingException("shard key name must not be blank");
        }
        this.name = name.trim();
        this.value = Objects.requireNonNull(value, "shard value must not be null");
    }

    public static ShardKey of(String name, Object value) {
        return new ShardKey(name, ShardValue.of(value));
    }

    public String name() {
        return name;
    }

    public ShardValue value() {
        return value;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof ShardKey other && name.equals(other.name) && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value);
    }

    @Override
    public String toString() {
        return "ShardKey{name='" + name + "', value=" + value + '}';
    }
}
