package com.xjtu.iron.storage.routing.integration.shardingsphere.proxy;

import com.xjtu.iron.relational.api.statement.SqlRoute;
import com.xjtu.iron.storage.routing.api.exception.StorageRoutingException;
import com.xjtu.iron.storage.routing.api.route.StorageRouteMode;
import com.xjtu.iron.storage.routing.api.route.storage.StorageRoute;
import com.xjtu.iron.storage.routing.integration.relational.StorageRouteToSqlRouteBridge;
import java.util.Objects;

/**
 * ShardingSphere-Proxy JDBC DataSource 桥接器。
 *
 * <p>返回的是指向外部 ShardingSphere-Proxy 的 {@link SqlRoute} 和 SQL 应使用的逻辑表名。默认构造方式
 * 使用 Relational Access 的默认 DataSource；应用注册多个 DataSource 时，可通过命名构造方式指定 Proxy
 * DataSource。该桥接器不部署 Proxy、不复制分片规则，也不解析或改写 SQL。</p>
 */
public final class ShardingSphereProxyStorageRouteToSqlRouteBridge implements StorageRouteToSqlRouteBridge {

    private final SqlRoute sqlRoute;

    public ShardingSphereProxyStorageRouteToSqlRouteBridge() {
        this(null);
    }

    public ShardingSphereProxyStorageRouteToSqlRouteBridge(String dataSourceKey) {
        this.sqlRoute = dataSourceKey == null || dataSourceKey.isBlank() ? SqlRoute.defaultRoute() : SqlRoute.of(dataSourceKey);
    }

    @Override
    public SqlRoute toSqlRoute(StorageRoute route) {
        requireProxyRoute(route);
        return sqlRoute;
    }

    @Override
    public String requireTableName(StorageRoute route) {
        StorageRoute required = requireProxyRoute(route);
        String logicalTable = required.logicalTable();
        if (logicalTable == null) {
            throw new StorageRoutingException("logicalTable is required for PROXY routing");
        }
        return logicalTable;
    }

    private StorageRoute requireProxyRoute(StorageRoute route) {
        StorageRoute required = Objects.requireNonNull(route, "route must not be null");
        if (required.mode() != StorageRouteMode.PROXY) {
            throw new StorageRoutingException("Only PROXY route can be bridged by this adapter: " + required.mode());
        }
        return required;
    }
}
