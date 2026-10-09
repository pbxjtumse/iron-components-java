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
 * <p>业务场景、逻辑表、分片键和扩展属性只保存在 RouteContext 中。
 * 同分片的订单、幂等、Outbox 可共享 shardInfo，但需要分别映射各自的 physicalLocation。</p>
 *
 * <p>固定直连可以没有分片键和 shardInfo；DIRECT_DATASOURCE 必须有完整物理位置。
 * mode 描述路由接入形态，不代表本轮已经实现 ShardingSphere / Proxy 适配。</p>
 */
public final class StorageRoute {

    private final StorageRouteMode mode;
    private final RouteContext context;
    private final ShardRouteInfo shardInfo;
    private final PhysicalStorageLocation physicalLocation;

    private StorageRoute(Builder builder) {
        this.mode = Objects.requireNonNull(builder.mode, "mode");
        this.context = builder.buildContext();
        this.shardInfo = builder.shardInfo;
        this.physicalLocation = builder.buildPhysicalLocation();
        if (mode == StorageRouteMode.DIRECT_DATASOURCE && physicalLocation == null) {
            throw new StorageRoutingException("physicalLocation is required for DIRECT_DATASOURCE");
        }
    }

    public static StorageRoute direct(String dataSourceKey, String tableName) {
        return fixedDirect(RouteContext.builder().build(), PhysicalStorageLocation.of(dataSourceKey, tableName));
    }

    /** 固定直连同时保留逻辑表；不会从物理表名反推逻辑表。 */
    public static StorageRoute direct(String logicalTable, String dataSourceKey, String tableName) {
        return fixedDirect(
                RouteContext.builder().logicalTable(logicalTable).build(),
                PhysicalStorageLocation.of(dataSourceKey, tableName));
    }

    /** 固定直连：保留本次输入上下文，不计算也不伪造分片编号。 */
    public static StorageRoute fixedDirect(RouteContext context, PhysicalStorageLocation physicalLocation) {
        return builder()
                .mode(StorageRouteMode.DIRECT_DATASOURCE)
                .context(Objects.requireNonNull(context, "context must not be null"))
                .physicalLocation(Objects.requireNonNull(physicalLocation, "physicalLocation must not be null"))
                .build();
    }

    /** 分片直连：同时保存原始输入、逻辑分片编号和最终物理库表。 */
    public static StorageRoute shardedDirect(RouteContext context, ShardRouteInfo shardInfo,
            PhysicalStorageLocation physicalLocation) {
        return builder()
                .mode(StorageRouteMode.DIRECT_DATASOURCE)
                .context(Objects.requireNonNull(context, "context must not be null"))
                .shardInfo(Objects.requireNonNull(shardInfo, "shardInfo must not be null"))
                .physicalLocation(Objects.requireNonNull(physicalLocation, "physicalLocation must not be null"))
                .build();
    }

    /** 中间件路由：保存逻辑输入及可选的预计算分片编号，不伪造应用侧物理库表。 */
    public static StorageRoute middleware(StorageRouteMode mode, RouteContext context) {
        return middleware(mode, context, null);
    }

    /** 中间件路由：保存逻辑输入及可选的预计算分片编号，不伪造应用侧物理库表。 */
    public static StorageRoute middleware(StorageRouteMode mode, RouteContext context, ShardRouteInfo shardInfo) {
        StorageRouteMode requiredMode = Objects.requireNonNull(mode, "mode must not be null");
        if (requiredMode == StorageRouteMode.DIRECT_DATASOURCE) {
            throw new StorageRoutingException("middleware route mode must not be DIRECT_DATASOURCE");
        }
        return builder()
                .mode(requiredMode)
                .context(Objects.requireNonNull(context, "context must not be null"))
                .shardInfo(shardInfo)
                .build();
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

    public ShardRouteInfo requireShardInfo() {
        if (shardInfo == null) {
            throw new StorageRoutingException("shardInfo is not available for route mode " + mode);
        }
        return shardInfo;
    }

    public PhysicalStorageLocation physicalLocation() {
        return physicalLocation;
    }

    public PhysicalStorageLocation requirePhysicalLocation() {
        if (physicalLocation == null) {
            throw new StorageRoutingException("physicalLocation is not available for route mode " + mode);
        }
        return physicalLocation;
    }

    public String routeName() {
        return context.routeName();
    }

    public String logicalTable() {
        return context.logicalTable();
    }

    /** 物理位置未知时返回 null，不代表中间件逻辑数据源。 */
    public String dataSourceKey() {
        return physicalLocation == null ? null : physicalLocation.dataSourceKey();
    }

    /** 物理位置未知时返回 null，不会用逻辑表冒充物理表。 */
    public String tableName() {
        return physicalLocation == null ? null : physicalLocation.tableName();
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

        private RouteContext buildContext() {
            return context == null ? RouteContext.builder().build() : context;
        }

        private PhysicalStorageLocation buildPhysicalLocation() {
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
        return "StorageRoute{mode=" + mode + ", context=" + context + ", shardInfo=" + shardInfo
                + ", physicalLocation=" + physicalLocation + '}';
    }
}
