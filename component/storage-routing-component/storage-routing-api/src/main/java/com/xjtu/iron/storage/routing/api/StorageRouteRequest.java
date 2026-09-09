package com.xjtu.iron.storage.routing.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 存储路由请求。
 *
 * <p>它是业务信息进入 StorageRouteResolver 前的统一载体。Resolver 不应该直接依赖 Order、Payment、Task 等业务对象，
 * 而是依赖这个稳定请求模型，从中读取 logicalTable、shardKeyName、shardKeyValue 等路由要素。</p>
 */
public final class StorageRouteRequest {

    private final String scene;
    private final String logicalTable;
    private final String shardKeyName;
    private final Object shardKeyValue;
    private final Map<String, Object> attributes;

    private StorageRouteRequest(Builder builder) {
        this.scene = normalize(builder.scene);
        this.logicalTable = requireText(builder.logicalTable, "logicalTable must not be blank");
        this.shardKeyName = requireText(builder.shardKeyName, "shardKeyName must not be blank");
        this.shardKeyValue = Objects.requireNonNull(builder.shardKeyValue, "shardKeyValue must not be null");
        this.attributes = immutableCopy(builder.attributes);
    }

    public static StorageRouteRequest of(String logicalTable, String shardKeyName, Object shardKeyValue) {
        return builder()
                .logicalTable(logicalTable)
                .shardKeyName(shardKeyName)
                .shardKeyValue(shardKeyValue)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String scene() {
        return scene;
    }

    public String logicalTable() {
        return logicalTable;
    }

    public String shardKeyName() {
        return shardKeyName;
    }

    public Object shardKeyValue() {
        return shardKeyValue;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }

    public Object attribute(String name) {
        return attributes.get(name);
    }

    private static String requireText(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new StorageRoutingException(message);
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static Map<String, Object> immutableCopy(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    @Override
    public String toString() {
        return "StorageRouteRequest{"
                + "scene='" + scene + '\''
                + ", logicalTable='" + logicalTable + '\''
                + ", shardKeyName='" + shardKeyName + '\''
                + ", shardKeyValue=" + shardKeyValue
                + ", attributes=" + attributes
                + '}';
    }

    public static final class Builder {

        private String scene;
        private String logicalTable;
        private String shardKeyName;
        private Object shardKeyValue;
        private Map<String, Object> attributes = Collections.emptyMap();

        private Builder() {
        }

        public Builder scene(String scene) {
            this.scene = scene;
            return this;
        }

        public Builder logicalTable(String logicalTable) {
            this.logicalTable = logicalTable;
            return this;
        }

        public Builder shardKeyName(String shardKeyName) {
            this.shardKeyName = shardKeyName;
            return this;
        }

        public Builder shardKeyValue(Object shardKeyValue) {
            this.shardKeyValue = shardKeyValue;
            return this;
        }

        public Builder attributes(Map<String, Object> attributes) {
            this.attributes = attributes == null ? Collections.emptyMap() : attributes;
            return this;
        }

        public Builder attribute(String name, Object value) {
            if (this.attributes.isEmpty()) {
                this.attributes = new LinkedHashMap<>();
            } else if (!(this.attributes instanceof LinkedHashMap)) {
                this.attributes = new LinkedHashMap<>(this.attributes);
            }
            this.attributes.put(Objects.requireNonNull(name, "attribute name must not be null"), value);
            return this;
        }

        public StorageRouteRequest build() {
            return new StorageRouteRequest(this);
        }
    }
}
