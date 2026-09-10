package com.xjtu.iron.storage.routing.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 一次逻辑表访问的路由结果。
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
 * <p>logicalTable 保留请求中的逻辑表，shardInfo 表达分片结果，physicalLocation 表达直连模式下的物理落点。
 * 同分片的订单、幂等、Outbox 可以共享 shardInfo，但需要分别映射各自的物理表，不能直接复用订单表名。</p>
 *
 * <p>StorageRoute 本身不负责计算 shard，也不负责建立 JDBC Connection。固定直连路由可以没有 shardInfo；
 * 中间件委托模式可以没有 physicalLocation，该枚举值不代表当前已经实现对应的中间件适配。</p>
 */
public final class StorageRoute {

    private final StorageRouteMode mode;

    /** 当前路由场景，例如 order-create、idempotency-try-acquire。 */
    private final String routeName;

    /** 请求中的逻辑表，例如 business_order；固定直连快捷方法可以不提供。 */
    private final String logicalTable;

    /** 分片计算结果。 */
    private final ShardRouteInfo shardInfo;

    /** 最终物理位置，例如 order-db-05.order_56。 */
    private final PhysicalStorageLocation physicalLocation;

    /** 原始分片字段信息，用于日志和诊断。 */
    private final String shardKeyName;

    private final Object shardKeyValue;

    /** 业务扩展信息，例如 tenantId、routeRule；已建模的分片与物理位置应从专用字段读取。 */
    private final Map<String, Object> attributes;

    private StorageRoute(Builder builder) {
        this.mode = Objects.requireNonNull(builder.mode, "mode");
        this.routeName = normalize(builder.routeName);
        this.logicalTable = normalize(builder.logicalTable);
        this.shardInfo = builder.shardInfo;
        this.physicalLocation = builder.buildPhysicalLocation();
        if (mode == StorageRouteMode.DIRECT_DATASOURCE && physicalLocation == null) {
            throw new StorageRoutingException("physicalLocation is required for DIRECT_DATASOURCE");
        }
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

    /** 创建同时保留逻辑表与物理表的直连路由，不从物理表名反推逻辑表。 */
    public static StorageRoute direct(String logicalTable, String dataSourceKey, String tableName) {
        return builder()
                .logicalTable(logicalTable)
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

    public String logicalTable() {
        return logicalTable;
    }

    public ShardRouteInfo shardInfo() {
        return shardInfo;
    }

    public PhysicalStorageLocation physicalLocation() {
        return physicalLocation;
    }

    /**
     * 物理数据源的便捷读取方法。
     *
     * <p>与 physicalLocation 共用同一份状态；物理位置未知时返回 null，不代表中间件逻辑数据源。</p>
     */
    public String dataSourceKey() {
        return physicalLocation == null ? null : physicalLocation.dataSourceKey();
    }

    /** 返回物理表名；不会用 logicalTable 冒充尚未解析的物理表。 */
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
        private String logicalTable;
        private ShardRouteInfo shardInfo;
        // Builder 暂存库表字段，build 时一次性校验，避免旧式链式调用依赖设置顺序。
        private String dataSourceKey;
        private String tableName;
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

        public Builder logicalTable(String logicalTable) {
            this.logicalTable = logicalTable;
            return this;
        }

        public Builder shardInfo(ShardRouteInfo shardInfo) {
            this.shardInfo = shardInfo;
            return this;
        }

        public Builder physicalLocation(PhysicalStorageLocation physicalLocation) {
            this.dataSourceKey = physicalLocation == null ? null : physicalLocation.dataSourceKey();
            this.tableName = physicalLocation == null ? null : physicalLocation.tableName();
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

        /** 复制请求扩展属性；不持有调用方 Map，也不修改调用方 Map。 */
        public Builder attributes(Map<String, Object> attributes) {
            this.attributes = new LinkedHashMap<>();
            if (attributes != null) {
                attributes.forEach(this::attribute);
            }
            return this;
        }

        public Builder attribute(String name, Object value) {
            this.attributes.put(Objects.requireNonNull(name, "attribute name must not be null"), value);
            return this;
        }

        private PhysicalStorageLocation buildPhysicalLocation() {
            if (normalize(dataSourceKey) == null && normalize(tableName) == null) {
                return null;
            }
            return PhysicalStorageLocation.of(dataSourceKey, tableName);
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
                ", logicalTable='" + logicalTable + '\'' +
                ", shardInfo=" + shardInfo +
                ", physicalLocation=" + physicalLocation +
                '}';
    }
}
