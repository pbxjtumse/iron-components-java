package com.xjtu.iron.storage.routing.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 一次存储访问的最终路由描述。
 *
 * <p>StorageRoute 连接三个阶段：</p>
 * <pre>
 * shardKey
 *    |
 *    v
 * ShardRouteInfo        (逻辑分片结果)
 *    |
 *    v
 * PhysicalStorageLocation (物理存储位置)
 *    |
 *    v
 * Relational Access
 * </pre>
 *
 * <p>StorageRoute 本身不负责计算 shard，也不负责建立 JDBC Connection。</p>
 */
public final class StorageRoute {

    private final StorageRouteMode mode;

    /** 当前路由场景，例如 order-create、idempotency-try-acquire。 */
    private final String routeName;

    /** 分片计算结果。 */
    private final ShardRouteInfo shardInfo;

    /** 最终物理位置，例如 order-db-05.order_56。 */
    private final PhysicalStorageLocation physicalLocation;

    /** 原始分片字段信息，用于日志和诊断。 */
    private final String shardKeyName;

    private final Object shardKeyValue;

    /** 扩展信息，例如 logicalTable、routeRule 等。 */
    private final Map<String, Object> attributes;

    private StorageRoute(Builder builder) {
        this.mode = Objects.requireNonNull(builder.mode, "mode");
        this.routeName = normalize(builder.routeName);
        this.shardInfo = builder.shardInfo;
        this.physicalLocation = builder.physicalLocation;
        this.shardKeyName = normalize(builder.shardKeyName);
        this.shardKeyValue = builder.shardKeyValue;
        this.attributes = builder.attributes == null || builder.attributes.isEmpty()
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.attributes));
    }

    public static StorageRoute direct(String dataSourceKey, String tableName) {
        return builder()
                .mode(StorageRouteMode.DIRECT_DATASOURCE)
                .physicalLocation(PhysicalStorageLocation.of(dataSourceKey, tableName))
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

    public ShardRouteInfo shardInfo() {
        return shardInfo;
    }

    public PhysicalStorageLocation physicalLocation() {
        return physicalLocation;
    }

    /**
     * 兼容读取方法。
     *
     * <p>真正模型已经迁移到 PhysicalStorageLocation。</p>
     */
    public String dataSourceKey() {
        return physicalLocation == null ? null : physicalLocation.dataSourceKey();
    }

    public String tableName() {
        return physicalLocation == null ? null : physicalLocation.tableName();
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
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static final class Builder {

        private StorageRouteMode mode = StorageRouteMode.DIRECT_DATASOURCE;
        private String routeName;
        private ShardRouteInfo shardInfo;
        private PhysicalStorageLocation physicalLocation;
        private String shardKeyName;
        private Object shardKeyValue;
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

        public Builder shardInfo(ShardRouteInfo shardInfo) {
            this.shardInfo = shardInfo;
            return this;
        }

        public Builder physicalLocation(PhysicalStorageLocation physicalLocation) {
            this.physicalLocation = physicalLocation;
            return this;
        }

        public Builder dataSourceKey(String dataSourceKey) {
            this.physicalLocation = PhysicalStorageLocation.of(dataSourceKey,
                    this.physicalLocation == null ? null : this.physicalLocation.tableName());
            return this;
        }

        public Builder tableName(String tableName) {
            this.physicalLocation = PhysicalStorageLocation.of(
                    this.physicalLocation == null ? null : this.physicalLocation.dataSourceKey(),
                    tableName);
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
                ", shardInfo=" + shardInfo +
                ", physicalLocation=" + physicalLocation +
                '}';
    }
}
