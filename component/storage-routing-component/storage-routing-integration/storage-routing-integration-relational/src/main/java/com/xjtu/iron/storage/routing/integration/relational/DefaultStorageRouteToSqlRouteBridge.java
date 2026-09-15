package com.xjtu.iron.storage.routing.integration.relational;

import com.xjtu.iron.relational.api.statement.SqlRoute;
import com.xjtu.iron.storage.routing.api.PhysicalStorageLocation;
import com.xjtu.iron.storage.routing.api.StorageRoute;
import com.xjtu.iron.storage.routing.api.StorageRouteMode;
import com.xjtu.iron.storage.routing.api.StorageRoutingException;

import java.util.Objects;

/**
 * 默认直连模式桥接器。
 *
 * <p>当前只处理 DIRECT_DATASOURCE。ShardingSphere-JDBC / Proxy 场景需要专门 Adapter 决定
 * SqlRoute 是否走默认逻辑 DataSource、Hint 或代理入口，本类不提前替它们做假设。</p>
 */
public final class DefaultStorageRouteToSqlRouteBridge implements StorageRouteToSqlRouteBridge {

    @Override
    public SqlRoute toSqlRoute(StorageRoute route) {
        return SqlRoute.of(requireDirectLocation(route).dataSourceKey());
    }

    @Override
    public String requireTableName(StorageRoute route) {
        return requireDirectLocation(route).tableName();
    }

    private PhysicalStorageLocation requireDirectLocation(StorageRoute route) {
        Objects.requireNonNull(route, "route must not be null");
        if (route.mode() != StorageRouteMode.DIRECT_DATASOURCE) {
            throw new StorageRoutingException("Only DIRECT_DATASOURCE route can be bridged to Relational Access: " + route.mode());
        }
        PhysicalStorageLocation location = route.location();
        if (location == null) {
            throw new StorageRoutingException("DIRECT_DATASOURCE route must contain physical location");
        }
        return location;
    }
}
