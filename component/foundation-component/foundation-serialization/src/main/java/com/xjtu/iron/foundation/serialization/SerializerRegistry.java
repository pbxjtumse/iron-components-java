package com.xjtu.iron.foundation.serialization;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 轻量序列化器注册表。
 */
public final class SerializerRegistry {

    private final Map<String, Serializer> serializers = new LinkedHashMap<>();

    public void register(String name, Serializer serializer) {
        if (name == null || name.isBlank()) { throw new IllegalArgumentException("name must not be blank"); }
        serializers.put(name, serializer);
    }

    public Optional<Serializer> find(String name) { return Optional.ofNullable(serializers.get(name)); }

    public Serializer require(String name) {
        return find(name).orElseThrow(() -> new IllegalArgumentException("serializer not found: " + name));
    }
}
