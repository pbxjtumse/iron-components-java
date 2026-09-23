package com.xjtu.iron.idempotent.integration.storage.routing;

import com.xjtu.iron.idempotent.api.execution.IdempotencyRequest;
import com.xjtu.iron.idempotent.api.recovery.IdempotencyRecoveryRequest;
import com.xjtu.iron.storage.routing.api.key.CompositeShardKey;
import com.xjtu.iron.storage.routing.api.key.ShardKey;
import com.xjtu.iron.storage.routing.api.route.RouteContext;

import java.util.Objects;

/** 默认按幂等 key 计算分片的 RouteContextFactory。
 * <p><b>流程阅读编号：I1.1：默认分片输入。</b>编号按 I（幂等）、R（路由）、D（数据访问）分组，不表示所有分支均依次执行。</p>
 * <ul>
 *     <li>1. 将幂等 key 包装成单字段 CompositeShardKey，再附上逻辑表及存储元数据。</li>
 *     <li>2. businessRouteKey 在默认实现中只是属性，不替代幂等 key 参与分片；自定义业务分片应预绑定或替换 Factory。</li>
 *     <li>3. 这里只构造 RouteContext，不计算 shardId，也不获取数据库连接。</li>
 * </ul>
 */
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
