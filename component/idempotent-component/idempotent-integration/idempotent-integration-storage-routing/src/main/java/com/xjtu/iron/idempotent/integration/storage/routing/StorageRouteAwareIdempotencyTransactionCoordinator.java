package com.xjtu.iron.idempotent.integration.storage.routing;

import com.xjtu.iron.idempotent.core.transaction.IdempotencyTransactionCoordinator;
import com.xjtu.iron.idempotent.core.transaction.IdempotencyTransactionalWork;
import com.xjtu.iron.idempotent.integration.transaction.TransactionTemplateIdempotencyTransactionCoordinator;
import com.xjtu.iron.storage.routing.api.StorageRoute;
import com.xjtu.iron.storage.routing.api.StorageRouteContext;
import com.xjtu.iron.storage.routing.api.StorageRouteMode;
import com.xjtu.iron.transaction.api.execution.TransactionExecutorResolver;
import java.util.Objects;

/** Tx-B 只消费已经绑定的路由，不重新 hash，不把业务 routeKey 当物理 dataSourceKey。 */
public final class StorageRouteAwareIdempotencyTransactionCoordinator implements IdempotencyTransactionCoordinator {
    private final StorageRouteContext routeContext;
    private final TransactionExecutorResolver executorResolver;

    public StorageRouteAwareIdempotencyTransactionCoordinator(StorageRouteContext routeContext, TransactionExecutorResolver executorResolver) {
        this.routeContext = Objects.requireNonNull(routeContext, "routeContext");
        this.executorResolver = Objects.requireNonNull(executorResolver, "executorResolver");
    }

    @Override
    public <T> T executeRequired(String transactionName, String routeKey, IdempotencyTransactionalWork<T> work) throws Exception {
        StorageRoute route = routeContext.requireCurrent();
        if (route.mode() != StorageRouteMode.DIRECT_DATASOURCE) throw new IllegalStateException("direct transaction coordinator requires DIRECT_DATASOURCE");
        return new TransactionTemplateIdempotencyTransactionCoordinator(executorResolver.resolve(route.dataSourceKey()))
                .executeRequired(transactionName, routeKey, work);
    }
}
