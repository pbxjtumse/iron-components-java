package com.xjtu.iron.storage.routing.integration.shardingsphere.proxy;

import com.xjtu.iron.storage.routing.api.exception.StorageRoutingException;
import com.xjtu.iron.storage.routing.api.resolver.StorageRouteResolver;
import com.xjtu.iron.storage.routing.api.route.RouteContext;
import com.xjtu.iron.storage.routing.api.route.StorageRouteMode;
import com.xjtu.iron.storage.routing.api.route.storage.StorageRoute;
import java.util.Objects;

/**
 * 将完整路由输入标记为由 ShardingSphere-Proxy 继续解析的逻辑路由。
 *
 * <p>本解析器不计算物理库表，也不在应用内加载 ShardingSphere。它只校验逻辑表和分片键已经准备完成，
 * 随后保留原始 {@link RouteContext}，让 Repository 使用逻辑表并在 SQL 中携带分片列，最终由外部
 * ShardingSphere-Proxy 完成 route / rewrite / execute。</p>
 */
public final class ShardingSphereProxyStorageRouteResolver implements StorageRouteResolver {

    @Override
    public StorageRoute resolve(RouteContext context) {
        RouteContext required = Objects.requireNonNull(context, "context must not be null");
        if (required.logicalTable() == null) {
            throw new StorageRoutingException("logicalTable is required for PROXY routing");
        }
        required.requireShardKey();
        return StorageRoute.builder().mode(StorageRouteMode.PROXY).context(required).build();
    }
}
