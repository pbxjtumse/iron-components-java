package com.xjtu.iron.idempotent.provider.mybatis.routing;

import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryQuery;
import com.xjtu.iron.idempotent.api.storage.IdempotencyStorageContext;
import com.xjtu.iron.relational.mybatis.MyBatisTableNameValidator;

import java.util.List;

/** 固定物理数据库和物理表的幂等路由。 */
public final class FixedIdempotencyPhysicalRouteResolver implements IdempotencyPhysicalRouteResolver {

    /** 唯一物理目标。 */
    private final IdempotencyPhysicalRoute route;

    public FixedIdempotencyPhysicalRouteResolver(String dataSourceKey, String tableName) {
        this.route = IdempotencyPhysicalRoute.of(
                dataSourceKey,
                MyBatisTableNameValidator.requireValid(tableName, "iron_idempotency_record")
        );
    }

    public static FixedIdempotencyPhysicalRouteResolver defaultDataSource(String tableName) {
        return new FixedIdempotencyPhysicalRouteResolver(null, tableName);
    }

    @Override
    public IdempotencyPhysicalRoute resolvePoint(
            IdempotencyStorageContext storageContext,
            String namespace,
            String idempotencyKey
    ) {
        return route;
    }

    @Override
    public List<IdempotencyPhysicalRoute> resolveRecoveryRoutes(IdempotencyRecoveryQuery query) {
        return List.of(route);
    }
}
