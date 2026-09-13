package com.xjtu.iron.storage.routing.core.resolver;

import com.xjtu.iron.storage.routing.api.PhysicalStorageLocation;
import com.xjtu.iron.storage.routing.api.RouteContext;
import com.xjtu.iron.storage.routing.api.ShardRouteInfo;
import com.xjtu.iron.storage.routing.api.StorageRoute;
import com.xjtu.iron.storage.routing.api.StorageRoutingException;
import com.xjtu.iron.storage.routing.api.mapping.RouteMappingStrategy;
import com.xjtu.iron.storage.routing.api.resolver.ShardResolver;
import com.xjtu.iron.storage.routing.api.resolver.StorageRouteResolver;

import java.util.Objects;

/**
 * 直连存储路由编排器。
 *
 * <p>从 RouteContext 取出 CompositeShardKey，交给 ShardResolver 计算编号，再交给 RouteMappingStrategy
 * 生成物理位置，最终组装 StorageRoute(context, shardInfo, location)。场景、表族、扩展属性不进入哈希算法。</p>
 *
 * <p>当前映射策略由构造函数指定，不会根据 logicalTable 自动选择规则。各表族需要配置自己的映射策略。</p>
 */
public final class DefaultStorageRouteResolver implements StorageRouteResolver {

    private final ShardResolver shardResolver;
    private final RouteMappingStrategy routeMappingStrategy;

    public DefaultStorageRouteResolver(ShardResolver shardResolver, RouteMappingStrategy routeMappingStrategy) {
        this.shardResolver = Objects.requireNonNull(shardResolver, "shardResolver must not be null");
        this.routeMappingStrategy = Objects.requireNonNull(routeMappingStrategy, "routeMappingStrategy must not be null");
    }

    @Override
    public StorageRoute resolve(RouteContext context) {
        Objects.requireNonNull(context, "context must not be null");
        ShardRouteInfo shardInfo = shardResolver.resolve(context.requireShardKey());
        if (shardInfo == null) {
            throw new StorageRoutingException("ShardResolver must return shardInfo");
        }
        PhysicalStorageLocation location = routeMappingStrategy.map(shardInfo);
        if (location == null) {
            throw new StorageRoutingException("RouteMappingStrategy must return physicalLocation");
        }
        // 原样保留不可变上下文，不复制为另一组平铺字段，也不丢失复合键的任何字段。
        return StorageRoute.builder().context(context).shardInfo(shardInfo).location(location).build();
    }
}
