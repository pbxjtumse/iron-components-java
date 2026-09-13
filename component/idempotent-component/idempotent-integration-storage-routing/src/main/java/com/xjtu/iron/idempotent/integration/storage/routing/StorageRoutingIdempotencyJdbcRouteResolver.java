package com.xjtu.iron.idempotent.integration.storage.routing;

import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryQuery;
import com.xjtu.iron.idempotent.api.storage.IdempotencyStorageContext;
import com.xjtu.iron.idempotent.provider.jdbc.routing.IdempotencyJdbcRoute;
import com.xjtu.iron.idempotent.provider.jdbc.routing.IdempotencyJdbcRouteResolver;
import com.xjtu.iron.storage.routing.api.CompositeShardKey;
import com.xjtu.iron.storage.routing.api.RouteContext;
import com.xjtu.iron.storage.routing.api.ShardKey;
import com.xjtu.iron.storage.routing.api.ShardRouteInfo;
import com.xjtu.iron.storage.routing.api.StorageRoute;
import com.xjtu.iron.storage.routing.api.StorageRouteContext;
import com.xjtu.iron.storage.routing.api.StorageRoutingException;
import com.xjtu.iron.storage.routing.api.mapping.RouteMappingStrategy;
import com.xjtu.iron.storage.routing.api.resolver.StorageRouteResolver;
import com.xjtu.iron.storage.routing.integration.relational.StorageRouteToSqlRouteBridge;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * IdempotencyStorageContext 到 Storage Routing 的正式桥接实现。
 *
 * <p>这层只解决“幂等状态最终写到哪个 dataSource / physical table”，不参与 ownerToken/version、
 * PROCESSING/SUCCESS/FAILED/DISCARDED 等幂等状态机。</p>
 *
 * <p>最重要的约束是：同一个业务分片可以共享 {@link ShardRouteInfo}，但业务表、幂等表、Outbox 表
 * 必须分别做物理表映射。Storage Routing 的默认 resolver 只拥有一套 tablePrefix，不能把它返回的
 * business location 直接当成 idempotency location；因此本实现会复用 shardInfo，再通过
 * idempotencyMappingStrategy 重新映射幂等表位置。</p>
 *
 * <pre>
 * Business Route
 *   RouteContext + CompositeShardKey
 *       -> shardInfo(56, db=5, localTable=6)
 *       -> business_order_56
 *
 * Idempotency Storage
 *   reuse same shardInfo
 *       -> idempotencyMappingStrategy
 *       -> iron_idempotency_record_56
 * </pre>
 *
 * <p>点查/写入优先复用 {@link StorageRouteContext} 中已经绑定的业务 shardKey/shardInfo，保证业务表与幂等表
 * 落在同一数据库分片。没有绑定业务路由时，才使用 IdempotencyStorageContext.shardKey 构造稳定 fallback key，
 * 交给 StorageRouteResolver 计算 shardInfo。</p>
 *
 * <p>Recovery 扫描是二维问题：外部 Reliable Task 先枚举物理 shard 并打开 StorageRouteContext，
 * 幂等组件再在该物理 shard 内按 scanBucket 查询。scanBucket 从来不等于物理表号。</p>
 */
public final class StorageRoutingIdempotencyJdbcRouteResolver implements IdempotencyJdbcRouteResolver {

    public static final String FALLBACK_SHARD_FIELD = "idempotency_shard_key";

    private final String logicalTable;
    private final StorageRouteResolver storageRouteResolver;
    private final StorageRouteContext storageRouteContext;
    private final RouteMappingStrategy idempotencyMappingStrategy;
    private final StorageRouteToSqlRouteBridge relationalBridge;

    public StorageRoutingIdempotencyJdbcRouteResolver(
            String logicalTable,
            StorageRouteResolver storageRouteResolver,
            StorageRouteContext storageRouteContext,
            RouteMappingStrategy idempotencyMappingStrategy,
            StorageRouteToSqlRouteBridge relationalBridge
    ) {
        if (logicalTable == null || logicalTable.isBlank()) {
            throw new IllegalArgumentException("logicalTable must not be blank");
        }
        this.logicalTable = logicalTable.trim();
        this.storageRouteResolver = Objects.requireNonNull(storageRouteResolver, "storageRouteResolver must not be null");
        this.storageRouteContext = Objects.requireNonNull(storageRouteContext, "storageRouteContext must not be null");
        this.idempotencyMappingStrategy = Objects.requireNonNull(
                idempotencyMappingStrategy, "idempotencyMappingStrategy must not be null");
        this.relationalBridge = Objects.requireNonNull(relationalBridge, "relationalBridge must not be null");
    }

    @Override
    public IdempotencyJdbcRoute resolvePoint(
            IdempotencyStorageContext storageContext,
            String namespace,
            String idempotencyKey
    ) {
        Objects.requireNonNull(storageContext, "storageContext must not be null");

        Optional<StorageRoute> boundRoute = this.storageRouteContext.current();
        CompositeShardKey shardKey = boundRoute
                .map(StorageRoute::context)
                .map(RouteContext::shardKey)
                .orElseGet(() -> fallbackShardKey(storageContext));

        RouteContext context = baseContext(storageContext.getStoreName(), namespace, storageContext.getScanBucket())
                .shardKey(shardKey)
                .attribute("idempotency.key", idempotencyKey)
                .build();

        // 已经有业务路由时，优先复用它算好的 shardInfo；这比再次 hash 更强，避免算法/键规范漂移。
        ShardRouteInfo boundShard = boundRoute.map(StorageRoute::shardInfo).orElse(null);
        if (boundShard != null) {
            return toJdbcRoute(remap(context, boundShard));
        }

        // 独立调用场景由全局 StorageRouteResolver 计算 shardInfo；若是固定直连 resolver 没有 shardInfo，
        // 则保留 resolver 自己给出的物理位置。
        StorageRoute resolved = storageRouteResolver.resolve(context);
        return toJdbcRoute(remapIfSharded(context, resolved));
    }

    @Override
    public List<IdempotencyJdbcRoute> resolveRecoveryRoutes(IdempotencyRecoveryQuery query) {
        Objects.requireNonNull(query, "query must not be null");

        Optional<StorageRoute> boundRoute = storageRouteContext.current();
        RouteContext.Builder contextBuilder = baseContext(query.getStoreName(), query.getNamespace(), query.getScanBucket());
        boundRoute.map(StorageRoute::context).map(RouteContext::shardKey).ifPresent(contextBuilder::shardKey);
        RouteContext context = contextBuilder.build();

        // Reliable Task 已经枚举并绑定物理 shard 时，直接复用该 shardInfo 映射到幂等表。
        ShardRouteInfo boundShard = boundRoute.map(StorageRoute::shardInfo).orElse(null);
        if (boundShard != null) {
            return List.of(toJdbcRoute(remap(context, boundShard)));
        }

        try {
            StorageRoute resolved = storageRouteResolver.resolve(context);
            return List.of(toJdbcRoute(remapIfSharded(context, resolved)));
        } catch (StorageRoutingException error) {
            if (boundRoute.isEmpty()) {
                throw new StorageRoutingException(
                        "Recovery scan cannot resolve a sharded idempotency table without a bound StorageRoute. "
                                + "The external Reliable Task must enumerate a physical shard, open StorageRouteContext, "
                                + "and then scan scanBucket=" + query.getScanBucket(), error);
            }
            throw error;
        }
    }

    private RouteContext.Builder baseContext(String storeName, String namespace, int scanBucket) {
        return RouteContext.builder()
                .routeName(storeName)
                .logicalTable(logicalTable)
                .attribute("idempotency.namespace", namespace)
                .attribute("idempotency.scanBucket", scanBucket);
    }

    private CompositeShardKey fallbackShardKey(IdempotencyStorageContext storageContext) {
        return CompositeShardKey.of(ShardKey.of(FALLBACK_SHARD_FIELD, storageContext.getShardKey()));
    }

    /**
     * 只复用 shardInfo，不复用 business physical location。
     */
    private StorageRoute remap(RouteContext context, ShardRouteInfo shardInfo) {
        return StorageRoute.builder()
                .context(context)
                .shardInfo(shardInfo)
                .location(idempotencyMappingStrategy.map(shardInfo))
                .build();
    }

    private StorageRoute remapIfSharded(RouteContext context, StorageRoute resolved) {
        Objects.requireNonNull(resolved, "storageRouteResolver returned null");
        return resolved.shardInfo() == null ? resolved : remap(context, resolved.shardInfo());
    }

    private IdempotencyJdbcRoute toJdbcRoute(StorageRoute route) {
        String dataSourceKey = relationalBridge.toSqlRoute(route).dataSourceKey();
        String tableName = relationalBridge.requireTableName(route);
        return IdempotencyJdbcRoute.of(dataSourceKey, tableName);
    }
}
