package com.xjtu.iron.storage.routing.integration.shardingsphere.jdbc;

import com.xjtu.iron.storage.routing.api.exception.StorageRoutingException;
import com.xjtu.iron.storage.routing.api.resolver.StorageRouteResolver;
import com.xjtu.iron.storage.routing.api.route.RouteContext;
import com.xjtu.iron.storage.routing.api.route.StorageRouteMode;
import com.xjtu.iron.storage.routing.api.route.storage.StorageRoute;
import java.util.Objects;

/**
 * 将完整路由输入标记为由 ShardingSphere-JDBC 继续解析的逻辑路由。
 *
 * <p>本解析器不计算物理库表，也不复制 ShardingSphere 的分片算法。它只校验逻辑表和分片键已经准备完成，
 * 随后保留原始 {@link RouteContext}，让 Repository 把逻辑表和分片列写入 SQL，由 ShardingSphere-JDBC
 * 完成 route / rewrite / execute。</p>
 */
public final class ShardingSphereJdbcStorageRouteResolver implements StorageRouteResolver {

    @Override
    public StorageRoute resolve(RouteContext context) {
        RouteContext required = Objects.requireNonNull(context, "context must not be null");
        if (required.logicalTable() == null) {
            throw new StorageRoutingException("logicalTable is required for SHARDINGSPHERE_JDBC routing");
        }
        required.requireShardKey();
        return StorageRoute.middleware(StorageRouteMode.SHARDINGSPHERE_JDBC, required);
    }
}
