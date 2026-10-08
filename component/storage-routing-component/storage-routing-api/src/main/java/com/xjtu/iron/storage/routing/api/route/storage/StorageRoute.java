package com.xjtu.iron.storage.routing.api.route.storage;

import com.xjtu.iron.storage.routing.api.exception.StorageRoutingException;
import com.xjtu.iron.storage.routing.api.route.PhysicalStorageLocation;
import com.xjtu.iron.storage.routing.api.route.RouteContext;
import com.xjtu.iron.storage.routing.api.route.ShardRouteInfo;
import com.xjtu.iron.storage.routing.api.route.StorageRouteMode;

import java.util.Objects;

/**
 * 一次存储访问的组合式路由结果：输入上下文 + 分片结果 + 物理位置。
 *
 * <pre>{@code
 *   1. Direct 模式：
 *     mode            = DIRECT_DATASOURCE
 *     shardInfo       = shardId 37
 *     dataSourceKey   = db_03
 *     tableName       = business_order_07
 *   2.ShardingSphere-JDBC 模式：
 *     mode            = SHARDINGSPHERE_JDBC
 *     logicalTable    = business_order
 *     CompositeShardKey 保留
 *   3.Proxy 模式类似：
 *     mode            = PROXY
 *     logicalTable    = business_order
 *     physicalLocation = null
 *     physicalLocation = null
 * }</pre>
 *
 */
public final class StorageRoute {

    private final StorageRouteMode mode;
    private final RouteContext context;
    private final ShardRouteInfo shardInfo;
    private final PhysicalStorageLocation location;

    private StorageRoute(Builder builder) {
        this.mode = Objects.requireNonNull(builder.mode, "mode");
        this.context = builder.buildContext();
        this.shardInfo = builder.shardInfo;
        this.location = builder.buildLocation();
        if (mode == StorageRouteMode.DIRECT_DATASOURCE && location == null) {
            throw new StorageRoutingException("physicalLocation is required for DIRECT_DATASOURCE");
        }
    }

    public static StorageRoute direct(String dataSourceKey, String tableName) {
        return builder().location(PhysicalStorageLocation.of(dataSourceKey, tableName)).build();
    }

    /** 固定直连同时保留逻辑表；不会从物理表名反推逻辑表。 */
    public static StorageRoute direct(String logicalTable, String dataSourceKey, String tableName) {
        return builder().context(RouteContext.builder().logicalTable(logicalTable).build())
                .location(PhysicalStorageLocation.of(dataSourceKey, tableName)).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public StorageRouteMode mode() {
        return mode;
    }

    public RouteContext context() {
        return context;
    }

    public ShardRouteInfo shardInfo() {
        return shardInfo;
    }

    public PhysicalStorageLocation location() {
        return location;
    }

    /** 与 location() 指向同一份状态的便捷读取入口。 */
    public PhysicalStorageLocation physicalLocation() {
        return location;
    }

    public String routeName() {
        return context.routeName();
    }

    public String logicalTable() {
        return context.logicalTable();
    }

    /** 物理位置未知时返回 null，不代表中间件逻辑数据源。 */
    public String dataSourceKey() {
        return location == null ? null : location.dataSourceKey();
    }

    /** 物理位置未知时返回 null，不会用逻辑表冒充物理表。 */
    public String tableName() {
        return location == null ? null : location.tableName();
    }

    public static final class Builder {

        private StorageRouteMode mode = StorageRouteMode.DIRECT_DATASOURCE;
        private RouteContext context;
        private ShardRouteInfo shardInfo;
        // 暂存库表，build 时统一校验，允许 dataSourceKey/tableName 任意设置顺序。
        private String dataSourceKey;
        private String tableName;

        private Builder() {
        }

        public Builder mode(StorageRouteMode mode) {
            this.mode = mode;
            return this;
        }

        public Builder context(RouteContext context) {
            this.context = Objects.requireNonNull(context, "context must not be null");
            return this;
        }

        public Builder shardInfo(ShardRouteInfo shardInfo) {
            this.shardInfo = shardInfo;
            return this;
        }

        public Builder location(PhysicalStorageLocation location) {
            this.dataSourceKey = location == null ? null : location.dataSourceKey();
            this.tableName = location == null ? null : location.tableName();
            return this;
        }

        public Builder physicalLocation(PhysicalStorageLocation physicalLocation) {
            return location(physicalLocation);
        }

        public Builder dataSourceKey(String dataSourceKey) {
            this.dataSourceKey = dataSourceKey;
            return this;
        }

        public Builder tableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        private RouteContext buildContext() {
            return context == null ? RouteContext.builder().build() : context;
        }

        private PhysicalStorageLocation buildLocation() {
            if ((dataSourceKey == null || dataSourceKey.isBlank()) && (tableName == null || tableName.isBlank())) {
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
        return "StorageRoute{mode=" + mode + ", context=" + context + ", shardInfo=" + shardInfo + ", location=" + location + '}';
    }
}
