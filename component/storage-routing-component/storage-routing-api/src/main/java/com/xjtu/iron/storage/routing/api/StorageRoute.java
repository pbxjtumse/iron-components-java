package com.xjtu.iron.storage.routing.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 一次存储访问的最终路由描述。
 *
 * <p>StorageRoute 不负责计算 shard，也不负责打开 JDBC Connection。
 * 它是 ShardRouteInfo 和物理存储位置之间的桥梁。</p>
 *
 * <p>完整链路：</p>
 * <pre>
 * orderId
 *    |
 *    v
 * ShardResolver
 *    |
 *    v
 * ShardRouteInfo
 *    |
 *    v
 * StorageRoute
 *    |
 *    v
 * Relational Access
 * </pre>
 */
public final class StorageRoute {

    private final StorageRouteMode mode;

    /** 路由场景，例如 order-create、idempotency-try-acquire。 */
    private final String routeName;

    /** 目标数据源标识，例如 order-db-05。 */
    private final String dataSourceKey;

    /** 目标表，可以是物理表，也可以是逻辑表。 */
    private final String tableName;

    /** 分片字段，例如 order_id。 */
    private final String shardKeyName;

    /** 分片字段值，例如 order-10001。 */
    private final Object shardKeyValue;

    /**
     * 分片计算结果。
     *
     * <p>Phase 2 后优先使用该对象表达 shard 信息。</p>
     */
    private final ShardRouteInfo shardInfo;

    /** 扩展信息，例如 logicalTable、routeRule 等。 */
    private final Map<String, Object> attributes;

    private StorageRoute(Builder builder) {
        this.mode = Objects.requireNonNull(builder.mode, "mode");
        this.routeName = normalize(builder.routeName);
        this.dataSourceKey = normalize(builder.dataSourceKey);
        this.tableName = normalize(builder.tableName);
        this.shardKeyName = normalize(builder.shardKeyName);
        this.shardKeyValue = builder.shardKeyValue;
        this.shardInfo = builder.shardInfo;
        this.attributes = builder.attributes == null || builder.attributes.isEmpty()
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.attributes));
    }

    public static StorageRoute direct(String dataSourceKey, String tableName) {
        return builder()
                .mode(StorageRouteMode.DIRECT_DATASOURCE)
                .dataSourceKey(dataSourceKey)
                .tableName(tableName)
                .build();
    }

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

    public ShardRouteInfo shardInfo() {
        return shardInfo;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }

    public Object attribute(String name) {
        return attributes.get(name);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static final class Builder {

        private StorageRouteMode mode = StorageRouteMode.DIRECT_DATASOURCE;
        private String routeName;
        private String dataSourceKey;
        private String tableName;
        private String shardKeyName;
        private Object shardKeyValue;
        private ShardRouteInfo shardInfo;
        private Map<String, Object> attributes = new LinkedHashMap<>();

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

        public Builder shardInfo(ShardRouteInfo shardInfo) {
            this.shardInfo = shardInfo;
            return this;
        }

        public Builder attribute(String name, Object value) {
            this.attributes.put(name, value);
            return this;
        }

        public StorageRoute build() {
            return new StorageRoute(this);
        }
    }

    @Override
    public String toString() {
        return "StorageRoute{" +
                "mode=" + mode +
                ", routeName='" + routeName + '\'' +
                ", dataSourceKey='" + dataSourceKey + '\'' +
                ", tableName='" + tableName + '\'' +
                ", shardInfo=" + shardInfo +
                '}';
    }
}