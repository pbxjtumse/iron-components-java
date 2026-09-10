package com.xjtu.iron.storage.routing.core.resolver;

import com.xjtu.iron.storage.routing.api.PhysicalStorageLocation;
import com.xjtu.iron.storage.routing.api.StorageRoute;
import com.xjtu.iron.storage.routing.api.StorageRouteRequest;
import com.xjtu.iron.storage.routing.api.ShardRouteInfo;
import com.xjtu.iron.storage.routing.api.StorageRouteMode;
import com.xjtu.iron.storage.routing.api.StorageRoutingException;
import com.xjtu.iron.storage.routing.api.resolver.ShardRouteResolver;
import com.xjtu.iron.storage.routing.api.StorageRouteResolver;
import com.xjtu.iron.storage.routing.api.mapping.RouteMappingStrategy;

import java.util.Objects;

/**
 * 默认存储路由编排器。
 *
 * <p>负责串联直连模式的 Shard 计算与物理位置映射，并保留请求的逻辑表、场景、分片键和扩展信息。
 * 当前映射策略按构造时的表前缀工作，应为各表族配置对应策略；它不自动按 logicalTable 选择规则。</p>
 *
 * <pre>
 * Request
 *    |
 *    v
 * ShardRouteResolver
 *    |
 *    v
 * ShardRouteInfo
 *    |
 *    v
 * RouteMappingStrategy
 *    |
 *    v
 * PhysicalStorageLocation
 *    |
 *    v
 * StorageRoute
 * </pre>
 */
public final class DefaultStorageRouteResolver implements StorageRouteResolver {

    private final ShardRouteResolver shardRouteResolver;

    private final RouteMappingStrategy routeMappingStrategy;

    public DefaultStorageRouteResolver(
            ShardRouteResolver shardRouteResolver,
            RouteMappingStrategy routeMappingStrategy
    ) {
        this.shardRouteResolver = Objects.requireNonNull(shardRouteResolver, "shardRouteResolver must not be null");
        this.routeMappingStrategy = Objects.requireNonNull(routeMappingStrategy, "routeMappingStrategy must not be null");
    }

    @Override
    public StorageRoute resolve(StorageRouteRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        ShardRouteInfo shardRouteInfo = shardRouteResolver.resolve(request);
        if (shardRouteInfo == null) {
            throw new StorageRoutingException("ShardRouteResolver must return shardInfo");
        }
        PhysicalStorageLocation location = routeMappingStrategy.map(shardRouteInfo);
        if (location == null) {
            throw new StorageRoutingException("RouteMappingStrategy must return physicalLocation");
        }

        return StorageRoute.builder()
                .mode(StorageRouteMode.DIRECT_DATASOURCE)
                .routeName(request.scene())
                .logicalTable(request.logicalTable())
                .shardInfo(shardRouteInfo)
                .physicalLocation(location)
                .shardKeyName(request.shardKeyName())
                .shardKeyValue(request.shardKeyValue())
                .attributes(request.attributes())
                .build();
    }
}
