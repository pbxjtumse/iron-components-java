package com.xjtu.iron.storage.routing.core.resolver;

import com.xjtu.iron.storage.routing.api.exception.StorageRoutingException;
import com.xjtu.iron.storage.routing.api.mapping.RouteMappingStrategy;
import com.xjtu.iron.storage.routing.api.resolver.ShardResolver;
import com.xjtu.iron.storage.routing.api.resolver.StorageRouteResolver;
import com.xjtu.iron.storage.routing.api.route.PhysicalStorageLocation;
import com.xjtu.iron.storage.routing.api.route.RouteContext;
import com.xjtu.iron.storage.routing.api.route.ShardRouteInfo;
import com.xjtu.iron.storage.routing.api.route.storage.StorageRoute;

import java.util.Objects;

/**
 * 直连存储路由编排器。
 *
 * <p>从 RouteContext 取出 CompositeShardKey，交给 ShardResolver 计算编号，再交给 RouteMappingStrategy
 * 生成物理位置，最终组装 StorageRoute(context, shardInfo, location)。场景、表族、扩展属性不进入哈希算法。</p>
 *
 * <p>当前映射策略由构造函数指定，不会根据 logicalTable 自动选择规则。各表族需要配置自己的映射策略。</p>

 * <p><b>流程阅读编号：R1：组合分片计算和物理映射。</b>编号按 I（幂等）、R（路由）、D（数据访问）分组，不表示所有分支均依次执行。</p>
 * <ul>
 *     <li>1. RouteContext.requireShardKey 提取输入，交给 R2 ShardResolver 得到 shardInfo。</li>
 *     <li>2. MappingStrategy 将编号转为数据源键、物理表名；logicalTable 不会自动替换构造器配置的策略。</li>
 *     <li>3. 返回 StorageRoute 后由调用方绑定 R3 作用域或传给 R4 bridge；解析器自身不保存线程状态。</li>
 * </ul>
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
        // R1-1：算法只输出编号，不生成表名。
        ShardRouteInfo shardInfo = shardResolver.resolve(context.requireShardKey());
        if (shardInfo == null) {
            throw new StorageRoutingException("ShardResolver must return shardInfo");
        }
        // R1-2：映射只解释编号，不重新 hash 分片键。
        PhysicalStorageLocation location = routeMappingStrategy.map(shardInfo);
        if (location == null) {
            throw new StorageRoutingException("RouteMappingStrategy must return physicalLocation");
        }
        // 原样保留不可变上下文，不复制为另一组平铺字段，也不丢失复合键的任何字段。
        return StorageRoute.builder().context(context).shardInfo(shardInfo).location(location).build();
    }
}
