package com.xjtu.iron.idempotent.provider.jdbc.routing;

import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryQuery;
import com.xjtu.iron.idempotent.api.storage.IdempotencyStorageContext;

import java.util.List;
import java.util.Objects;

/**
 * 单库单表 / 已知固定库表场景的路由实现。
 *
 * <p>这是 V2 的兼容基线：即使业务完全没有启用 Storage Routing，JDBC 幂等仍然可以像原来一样工作。
 * 当 Storage Routing integration 存在时，Starter 会优先使用真正的路由实现。</p>
 */
public final class FixedIdempotencyJdbcRouteResolver implements IdempotencyJdbcRouteResolver {

    private final IdempotencyJdbcRoute route;

    public FixedIdempotencyJdbcRouteResolver(IdempotencyJdbcRoute route) {
        this.route = Objects.requireNonNull(route, "route must not be null");
    }

    public static FixedIdempotencyJdbcRouteResolver defaultDataSource(String tableName) {
        return new FixedIdempotencyJdbcRouteResolver(IdempotencyJdbcRoute.defaultDataSource(tableName));
    }

    public static FixedIdempotencyJdbcRouteResolver of(String dataSourceKey, String tableName) {
        return new FixedIdempotencyJdbcRouteResolver(IdempotencyJdbcRoute.of(dataSourceKey, tableName));
    }

    @Override
    public IdempotencyJdbcRoute resolvePoint(IdempotencyStorageContext storageContext, String namespace, String idempotencyKey) {
        Objects.requireNonNull(storageContext, "storageContext must not be null");
        return route;
    }

    @Override
    public List<IdempotencyJdbcRoute> resolveRecoveryRoutes(IdempotencyRecoveryQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        return List.of(route);
    }
}
