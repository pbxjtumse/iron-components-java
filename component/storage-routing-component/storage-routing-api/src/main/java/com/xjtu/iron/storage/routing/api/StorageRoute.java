package com.xjtu.iron.storage.routing.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 一次存储访问的路由结果。
 *
 * <p>StorageRoute 不是 JDBC 连接，也不是 SQL。它只表达“本次业务相关的存储记录应该去哪”。
 * 后续 Idempotency / Outbox / Task / Message 等技术组件都可以通过同一份 StorageRoute
 * 保证技术记录与业务记录落到同一个路由位置。</p>
 */
public final class StorageRoute {

    private final StorageRouteMode mode;
    private final String routeName;
    private final String dataSourceKey;
    private final String tableName;
    private final String shardKeyName;
    private final Object shardKeyValue;
    private final Map<String, Object> attributes;

    private StorageRoute(Builder builder) {
        this.mode = Objects.requireNonNull(builder.mode, "mode must not be null");
        this.routeName = normalize(builder.routeName);
        this.dataSourceKey = normalize(builder.dataSourceKey);
        this.tableName = normalize(builder.tableName);
        this.shardKeyName = normalize(builder.shardKeyName);
        this.shardKeyValue = builder.shardKeyValue;
        this.attributes = immutableCopy(builder.attributes);
    }

    /**
     * 创建直连 DataSource 模式路由。
     */
    public static StorageRoute direct(String dataSourceKey, String tableName) {
        return builder()
                .mode(StorageRouteMode.DIRECT_DATASOURCE)
                .dataSourceKey(dataSourceKey)
                .tableName(tableName)
                .build();
    }

    /**
     * 创建 Builder。
     */
    public static Builder builder() {
        return new Builder();
    }

    public StorageRouteMode mode() {
        return mode;
    }

    public String routeName() {
        return routeName;
    }

    public String dataSourceKey() {
        return dataSourceKey;
    }

    public String tableName() {
        return tableName;
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
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StorageRoute that)) {
            return false;
        }
        return mode == that.mode
                && Objects.equals(routeName, that.routeName)
                && Objects.equals(dataSourceKey, that.dataSourceKey)
                && Objects.equals(tableName, that.tableName)
                && Objects.equals(shardKeyName, that.shardKeyName)
                && Objects.equals(shardKeyValue, that.shardKeyValue)
                && Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, routeName, dataSourceKey, tableName, shardKeyName, shardKeyValue, attributes);
    }

    @Override
    public String toString() {
        return "StorageRoute{"
                + "mode=" + mode
                + ", routeName='" + routeName + '\''
                + ", dataSourceKey='" + dataSourceKey + '\''
                + ", tableName='" + tableName + '\''
                + ", shardKeyName='" + shardKeyName + '\''
                + ", shardKeyValue=" + shardKeyValue
                + ", attributes=" + attributes
                + '}';
    }

    public static final class Builder {

        private StorageRouteMode mode = StorageRouteMode.DIRECT_DATASOURCE;
        private String routeName;
        private String dataSourceKey;
        private String tableName;
        private String shardKeyName;
        private Object shardKeyValue;
        private Map<String, Object> attributes = Collections.emptyMap();

        private Builder() {
        }

        public Builder mode(StorageRouteMode mode) {
            this.mode = mode;
            return this;
        }

        public Builder routeName(String routeName) {
            this.routeName = routeName;
            return this;
        }

        public Builder dataSourceKey(String dataSourceKey) {
            this.dataSourceKey = dataSourceKey;
            return this;
        }

        public Builder tableName(String tableName) {
            this.tableName = tableName;
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

        public StorageRoute build() {
            return new StorageRoute(this);
        }
    }
}
