package com.xjtu.iron.idempotent.integration.storage.routing;

import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryQuery;
import com.xjtu.iron.idempotent.api.storage.IdempotencyStorageContext;
import com.xjtu.iron.idempotent.provider.jdbc.routing.IdempotencyJdbcRoute;
import com.xjtu.iron.idempotent.provider.jdbc.routing.IdempotencyJdbcRouteResolver;
import com.xjtu.iron.storage.routing.api.CompositeShardKey;
import com.xjtu.iron.storage.routing.api.RouteContext;
import com.xjtu.iron.storage.routing.api.ShardKey;
import com.xjtu.iron.storage.routing.api.StorageRoute;
import com.xjtu.iron.storage.routing.api.StorageRouteContext;
import com.xjtu.iron.storage.routing.api.StorageRoutingException;
import com.xjtu.iron.storage.routing.api.resolver.StorageRouteResolver;
import com.xjtu.iron.storage.routing.integration.relational.StorageRouteToSqlRouteBridge;

import java.util.List;
import java.util.Objects;

/**
 * IdempotencyStorageContext 到 Storage Routing 的正式桥接实现。
 *
 * <p>职责边界：</p>
 * <ul>
 *     <li>Idempotency 提供 storeName / shardKey / scanBucket；</li>
 *     <li>Storage Routing 负责把逻辑 routeName + logicalTable + shardKey 解析成 StorageRoute；</li>
 *     <li>Relational bridge 把 StorageRoute 转成 dataSourceKey，并提供物理表名；</li>
 *     <li>JdbcIdempotencyRepository 继续只负责幂等状态 SQL 和 owner/version CAS。</li>
 * </ul>
 *
 * <p>如果当前业务链已经通过 StorageRouteContext 绑定了业务分片，本实现优先复用其中的 CompositeShardKey。
 * 这样 order / idempotency / outbox 可以基于同一份分片身份分别映射自己的逻辑表，而不是幂等组件再次用 long shardKey
 * 猜一遍分片。只有当前没有路由上下文时，才使用 IdempotencyStorageContext.shardKey 构造单字段 fallback key。</p>
 */
public final class StorageRoutingIdempotencyJdbcRouteResolver implements IdempotencyJdbcRouteResolver {

    public static final String FALLBACK_SHARD_FIELD = "idempotency_shard_key";

    private final String logicalTable;
    private final StorageRouteResolver storageRouteResolver;
    private final StorageRouteContext storageRouteContext;
    private final StorageRouteToSqlRouteBridge relationalBridge;

    public StorageRoutingIdempotencyJdbcRouteResolver(
            String logicalTable,
            StorageRouteResolver storageRouteResolver,
            StorageRouteContext storageRouteContext,
            StorageRouteToSqlRouteBridge relationalBridge
    ) {
        if (logicalTable == null || logicalTable.isBlank()) {
            throw new IllegalArgumentException("logicalTable must not be blank");
        }
        this.logicalTable = logicalTable.trim();
        this.storageRouteResolver = Objects.requireNonNull(storageRouteResolver, "storageRouteResolver must not be null");
        this.storageRouteContext = Objects.requireNonNull(storageRouteContext, "storageRouteContext must not be null");
        this.relationalBridge = Objects.requireNonNull(relationalBridge, "relationalBridge must not be null");
    }

    @Override
    public IdempotencyJdbcRoute resolvePoint(
            IdempotencyStorageContext storageContext,
            String namespace,
            String idempotencyKey
    ) {
        Objects.requireNonNull(storageContext, "storageContext must not be null");

        CompositeShardKey shardKey = storageRouteContext.current()
                .map(StorageRoute::context)
                .map(RouteContext::shardKey)
                .orElseGet(() -> CompositeShardKey.of(
                        ShardKey.of(FALLBACK_SHARD_FIELD, storageContext.getShardKey())));

        RouteContext context = RouteContext.builder()
                .routeName(storageContext.getStoreName())
                .logicalTable(logicalTable)
                .shardKey(shardKey)
                .attribute("idempotency.namespace", namespace)
                .attribute("idempotency.scanBucket", storageContext.getScanBucket())
                .build();

        return toJdbcRoute(storageRouteResolver.resolve(context));
    }

    @Override
    public List<IdempotencyJdbcRoute> resolveRecoveryRoutes(IdempotencyRecoveryQuery query) {
        Objects.requireNonNull(query, "query must not be null");

        RouteContext.Builder builder = RouteContext.builder()
                .routeName(query.getStoreName())
                .logicalTable(logicalTable)
                .attribute("idempotency.namespace", query.getNamespace())
                .attribute("idempotency.scanBucket", query.getScanBucket());

        storageRouteContext.current()
                .map(StorageRoute::context)
                .map(RouteContext::shardKey)
                .ifPresent(builder::shardKey);

        try {
            return List.of(toJdbcRoute(storageRouteResolver.resolve(builder.build())));
        } catch (StorageRoutingException error) {
            if (storageRouteContext.current().isEmpty()) {
                throw new StorageRoutingException(
                        "Recovery scan cannot resolve a sharded idempotency table without a bound StorageRoute. "
                                + "The external Reliable Task must enumerate a physical shard, open StorageRouteContext, "
                                + "and then scan scanBucket=" + query.getScanBucket(), error);
            }
            throw error;
        }
    }

    private IdempotencyJdbcRoute toJdbcRoute(StorageRoute route) {
        String dataSourceKey = relationalBridge.toSqlRoute(route).dataSourceKey();
        String tableName = relationalBridge.requireTableName(route);
        return IdempotencyJdbcRoute.of(dataSourceKey, tableName);
    }
}
