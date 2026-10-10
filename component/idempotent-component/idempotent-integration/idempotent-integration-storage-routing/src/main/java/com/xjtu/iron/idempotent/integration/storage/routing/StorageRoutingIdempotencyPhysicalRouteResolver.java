package com.xjtu.iron.idempotent.integration.storage.routing;

import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryQuery;
import com.xjtu.iron.idempotent.api.storage.IdempotencyStorageContext;
import com.xjtu.iron.idempotent.provider.mybatis.routing.IdempotencyPhysicalRoute;
import com.xjtu.iron.idempotent.provider.mybatis.routing.IdempotencyPhysicalRouteResolver;
import com.xjtu.iron.storage.routing.api.context.StorageRouteContext;
import com.xjtu.iron.storage.routing.api.exception.StorageRoutingException;
import com.xjtu.iron.storage.routing.api.key.CompositeShardKey;
import com.xjtu.iron.storage.routing.api.key.ShardKey;
import com.xjtu.iron.storage.routing.api.mapping.RouteMappingStrategy;
import com.xjtu.iron.storage.routing.api.resolver.StorageRouteResolver;
import com.xjtu.iron.storage.routing.api.route.PhysicalStorageLocation;
import com.xjtu.iron.storage.routing.api.route.RouteContext;
import com.xjtu.iron.storage.routing.api.route.ShardRouteInfo;
import com.xjtu.iron.storage.routing.api.route.StorageRouteMode;
import com.xjtu.iron.storage.routing.api.route.storage.StorageRoute;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 把幂等逻辑存储上下文转换为 MyBatis Access 使用的物理数据源与物理表。 */
public final class StorageRoutingIdempotencyPhysicalRouteResolver
        implements IdempotencyPhysicalRouteResolver {

    /** 幂等表族的逻辑表名。 */
    private final String logicalTable;

    /** 固定直连场景使用的幂等物理表名。 */
    private final String directTableName;

    /** 全局 Storage Route 解析器。 */
    private final StorageRouteResolver storageRouteResolver;

    /** 当前线程已经绑定的业务路由上下文。 */
    private final StorageRouteContext storageRouteContext;

    /** 幂等表独立的物理表映射策略。 */
    private final RouteMappingStrategy idempotencyMappingStrategy;

    /** ShardingSphere-JDBC 逻辑 DataSource 标识；默认数据源允许为空。 */
    private final String shardingSphereJdbcDataSourceKey;

    /** ShardingSphere-Proxy 连接 DataSource 标识；默认数据源允许为空。 */
    private final String proxyDataSourceKey;

    public StorageRoutingIdempotencyPhysicalRouteResolver(
            String logicalTable,
            String directTableName,
            StorageRouteResolver storageRouteResolver,
            StorageRouteContext storageRouteContext,
            RouteMappingStrategy idempotencyMappingStrategy,
            String shardingSphereJdbcDataSourceKey,
            String proxyDataSourceKey
    ) {
        this.logicalTable = requireText(logicalTable, "logicalTable");
        this.directTableName = requireText(directTableName, "directTableName");
        this.storageRouteResolver = Objects.requireNonNull(
                storageRouteResolver,
                "storageRouteResolver must not be null"
        );
        this.storageRouteContext = Objects.requireNonNull(
                storageRouteContext,
                "storageRouteContext must not be null"
        );
        this.idempotencyMappingStrategy = Objects.requireNonNull(
                idempotencyMappingStrategy,
                "idempotencyMappingStrategy must not be null"
        );
        this.shardingSphereJdbcDataSourceKey = normalize(shardingSphereJdbcDataSourceKey);
        this.proxyDataSourceKey = normalize(proxyDataSourceKey);
    }

    @Override
    public IdempotencyPhysicalRoute resolvePoint(
            IdempotencyStorageContext storageContext,
            String namespace,
            String idempotencyKey
    ) {
        Objects.requireNonNull(storageContext, "storageContext must not be null");
        Optional<StorageRoute> boundRoute = this.storageRouteContext.current();
        if (boundRoute.isPresent() && boundRoute.get().mode() != StorageRouteMode.DIRECT_DATASOURCE) {
            return toAccessRoute(boundRoute.get());
        }
        CompositeShardKey shardKey = boundRoute.map(StorageRoute::context)
                .map(RouteContext::shardKey)
                .orElseGet(() -> defaultShardKey(idempotencyKey));

        RouteContext context = baseContext(
                storageContext.getStoreName(),
                namespace,
                storageContext.getScanBucket()
        ).shardKey(shardKey)
                .attribute("idempotency.key", idempotencyKey)
                .build();

        ShardRouteInfo boundShard = boundRoute.map(StorageRoute::shardInfo).orElse(null);
        if (boundShard != null) {
            return toPhysicalRoute(remapBound(context, boundRoute.orElseThrow()));
        }
        if (boundRoute.isPresent()) {
            return toPhysicalRoute(
                    directIdempotencyRoute(context, boundRoute.get().dataSourceKey())
            );
        }
        StorageRoute resolved = storageRouteResolver.resolve(context);
        return toAccessRoute(normalizeResolvedRoute(context, resolved));
    }

    @Override
    public List<IdempotencyPhysicalRoute> resolveRecoveryRoutes(IdempotencyRecoveryQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        Optional<StorageRoute> boundRoute = storageRouteContext.current();
        if (boundRoute.isPresent() && boundRoute.get().mode() != StorageRouteMode.DIRECT_DATASOURCE) {
            return List.of(toAccessRoute(boundRoute.get()));
        }
        RouteContext.Builder contextBuilder = baseContext(
                query.getStoreName(),
                query.getNamespace(),
                query.getScanBucket()
        );
        boundRoute.map(StorageRoute::context)
                .map(RouteContext::shardKey)
                .ifPresent(contextBuilder::shardKey);
        RouteContext context = contextBuilder.build();

        ShardRouteInfo boundShard = boundRoute.map(StorageRoute::shardInfo).orElse(null);
        if (boundShard != null) {
            return List.of(toPhysicalRoute(remapBound(context, boundRoute.orElseThrow())));
        }
        if (boundRoute.isPresent()) {
            return List.of(toPhysicalRoute(
                    directIdempotencyRoute(context, boundRoute.get().dataSourceKey())
            ));
        }
        try {
            StorageRoute resolved = storageRouteResolver.resolve(context);
            return List.of(toAccessRoute(normalizeResolvedRoute(context, resolved)));
        } catch (StorageRoutingException failure) {
            throw new StorageRoutingException(
                    "Recovery scan requires a bound physical StorageRoute before scanning bucket="
                            + query.getScanBucket(),
                    failure
            );
        }
    }

    private RouteContext.Builder baseContext(String storeName, String namespace, int scanBucket) {
        return RouteContext.builder()
                .routeName(storeName)
                .logicalTable(logicalTable)
                .attribute("idempotency.namespace", namespace)
                .attribute("idempotency.scanBucket", scanBucket);
    }

    private CompositeShardKey defaultShardKey(String idempotencyKey) {
        return CompositeShardKey.of(ShardKey.of(
                DefaultIdempotencyRouteContextFactory.IDEMPOTENCY_KEY_FIELD,
                requireText(idempotencyKey, "idempotencyKey")
        ));
    }

    private StorageRoute remapBound(RouteContext context, StorageRoute bound) {
        StorageRoute mapped = remap(context, bound.requireShardInfo());
        if (!Objects.equals(mapped.dataSourceKey(), bound.dataSourceKey())) {
            throw new StorageRoutingException(
                    "bound route and idempotency mapping must use the same dataSourceKey"
            );
        }
        return mapped;
    }

    private StorageRoute remap(RouteContext context, ShardRouteInfo shardInfo) {
        return StorageRoute.shardedDirect(
                context,
                shardInfo,
                idempotencyMappingStrategy.map(shardInfo)
        );
    }

    private StorageRoute normalizeResolvedRoute(RouteContext context, StorageRoute resolved) {
        Objects.requireNonNull(resolved, "storageRouteResolver returned null");
        if (resolved.mode() != StorageRouteMode.DIRECT_DATASOURCE) {
            return resolved;
        }
        return resolved.shardInfo() == null
                ? directIdempotencyRoute(context, resolved.dataSourceKey())
                : remap(context, resolved.requireShardInfo());
    }

    private StorageRoute directIdempotencyRoute(RouteContext context, String dataSourceKey) {
        return StorageRoute.fixedDirect(
                context,
                PhysicalStorageLocation.of(dataSourceKey, directTableName)
        );
    }

    private IdempotencyPhysicalRoute toPhysicalRoute(StorageRoute route) {
        PhysicalStorageLocation location = route.requirePhysicalLocation();
        return IdempotencyPhysicalRoute.of(
                location.dataSourceKey(),
                location.tableName()
        );
    }

    private IdempotencyPhysicalRoute toAccessRoute(StorageRoute route) {
        if (route.mode() == StorageRouteMode.DIRECT_DATASOURCE) {
            return toPhysicalRoute(route);
        }
        String dataSourceKey = route.mode() == StorageRouteMode.SHARDING_SPHERE_JDBC
                ? shardingSphereJdbcDataSourceKey
                : proxyDataSourceKey;
        // 外层绑定的 middleware route 可能属于业务表。这里只继承中间件模式，
        // 幂等技术表必须始终使用自己的 logicalTable，不能泄漏业务表名。
        return IdempotencyPhysicalRoute.of(dataSourceKey, logicalTable);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
