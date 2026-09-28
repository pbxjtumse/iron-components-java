package com.xjtu.iron.storage.routing.integration.shardingsphere.jdbc;

import com.xjtu.iron.relational.api.statement.SqlRoute;
import com.xjtu.iron.storage.routing.api.exception.StorageRoutingException;
import com.xjtu.iron.storage.routing.api.route.StorageRouteMode;
import com.xjtu.iron.storage.routing.api.route.storage.StorageRoute;
import com.xjtu.iron.storage.routing.integration.relational.StorageRouteToSqlRouteBridge;
import java.util.Objects;

/**
 * ShardingSphere-JDBC 逻辑 DataSource 桥接器。
 *
 * <p>返回的是 ShardingSphere 逻辑 DataSource 的 {@link SqlRoute} 和 SQL 应使用的逻辑表名。物理数据源与
 * 物理表对应用方保持不可见。默认构造方式使用 Relational Access 的默认 DataSource；如果应用同时注册了
 * 多个逻辑 DataSource，可以通过命名构造方式指定其中的 ShardingSphere DataSource。</p>
 */
public final class ShardingSphereJdbcStorageRouteToSqlRouteBridge implements StorageRouteToSqlRouteBridge {

    private final SqlRoute sqlRoute;

    public ShardingSphereJdbcStorageRouteToSqlRouteBridge() {
        this(null);
    }

    public ShardingSphereJdbcStorageRouteToSqlRouteBridge(String dataSourceKey) {
        this.sqlRoute = dataSourceKey == null || dataSourceKey.isBlank() ? SqlRoute.defaultRoute() : SqlRoute.of(dataSourceKey);
    }

    @Override
    public SqlRoute toSqlRoute(StorageRoute route) {
        requireShardingSphereRoute(route);
        return sqlRoute;
    }

    @Override
    public String requireTableName(StorageRoute route) {
        StorageRoute required = requireShardingSphereRoute(route);
        String logicalTable = required.logicalTable();
        if (logicalTable == null) {
            throw new StorageRoutingException("logicalTable is required for SHARDINGSPHERE_JDBC routing");
        }
        return logicalTable;
    }

    private StorageRoute requireShardingSphereRoute(StorageRoute route) {
        StorageRoute required = Objects.requireNonNull(route, "route must not be null");
        if (required.mode() != StorageRouteMode.SHARDINGSPHERE_JDBC) {
            throw new StorageRoutingException("Only SHARDINGSPHERE_JDBC route can be bridged by this adapter: " + required.mode());
        }
        return required;
    }
}
