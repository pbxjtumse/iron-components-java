package com.xjtu.iron.idempotent.integration.storage.routing;

import com.xjtu.iron.idempotent.api.execution.IdempotencyRequest;
import com.xjtu.iron.idempotent.api.recovery.IdempotencyRecoveryRequest;
import com.xjtu.iron.storage.routing.api.key.CompositeShardKey;
import com.xjtu.iron.storage.routing.api.key.ShardKey;
import com.xjtu.iron.storage.routing.api.route.RouteContext;
import java.util.Objects;

/** 默认按幂等 key 计算分片的 RouteContextFactory。 */
public final class DefaultIdempotencyRouteContextFactory implements IdempotencyRouteContextFactory {

    public static final String IDEMPOTENCY_KEY_FIELD = "idempotency_key";

    private final String logicalTable;

    public DefaultIdempotencyRouteContextFactory(String logicalTable) {
        if (logicalTable == null || logicalTable.isBlank()) {
            throw new IllegalArgumentException("logicalTable must not be blank");
        }
        this.logicalTable = logicalTable.trim();
    }

    @Override
    public RouteContext create(IdempotencyRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return create(request.getStoreName(), request.getKey(), request.getRouteKey(), request.getScanBucket());
    }

    @Override
    public RouteContext create(IdempotencyRecoveryRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return create(request.getStoreName(), request.getKey(), request.getRouteKey(), request.getScanBucket());
    }

    private RouteContext create(String storeName, String idempotencyKey, String businessRouteKey, int scanBucket) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        return RouteContext.builder()
                .routeName(storeName)
                .logicalTable(logicalTable)
                .shardKey(CompositeShardKey.of(ShardKey.of(IDEMPOTENCY_KEY_FIELD, idempotencyKey)))
                .attribute("idempotency.key", idempotencyKey)
                .attribute("idempotency.businessRouteKey", businessRouteKey)
                .attribute("idempotency.scanBucket", scanBucket)
                .build();
    }
}
