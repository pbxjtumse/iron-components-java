package com.xjtu.iron.storage.routing.core.resolver;

import com.xjtu.iron.storage.routing.api.PhysicalStorageLocation;
import com.xjtu.iron.storage.routing.api.StorageRoute;
import com.xjtu.iron.storage.routing.api.StorageRouteRequest;
import com.xjtu.iron.storage.routing.api.ShardRouteInfo;
import com.xjtu.iron.storage.routing.api.resolver.ShardRouteResolver;
import com.xjtu.iron.storage.routing.api.resolver.StorageRouteResolver;
import com.xjtu.iron.storage.routing.api.mapping.RouteMappingStrategy;

import java.util.Objects;

/**
 * 默认存储路由编排器。
 *
 * <p>负责串联：Shard 计算 + 物理位置映射。</p>
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
        this.shardRouteResolver = Objects.requireNonNull(shardRouteResolver);
        this.routeMappingStrategy = Objects.requireNonNull(routeMappingStrategy);
    }

    @Override
    public StorageRoute resolve(StorageRouteRequest request) {
        ShardRouteInfo shardRouteInfo = shardRouteResolver.resolve(request);
        PhysicalStorageLocation location = routeMappingStrategy.map(shardRouteInfo);

        return StorageRoute.builder()
                .routeName(request.scene())
                .shardInfo(shardRouteInfo)
                .physicalLocation(location)
                .shardKeyName(request.shardKeyName())
                .shardKeyValue(request.shardKeyValue())
                .build();
    }
}
