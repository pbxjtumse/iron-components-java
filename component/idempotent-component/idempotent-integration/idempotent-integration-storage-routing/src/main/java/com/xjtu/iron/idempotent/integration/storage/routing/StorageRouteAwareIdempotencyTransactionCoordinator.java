package com.xjtu.iron.idempotent.integration.storage.routing;

import com.xjtu.iron.idempotent.core.transaction.IdempotencyTransactionCoordinator;
import com.xjtu.iron.idempotent.core.transaction.IdempotencyTransactionalWork;
import com.xjtu.iron.idempotent.integration.transaction.TransactionTemplateIdempotencyTransactionCoordinator;
import com.xjtu.iron.storage.routing.api.context.StorageRouteContext;
import com.xjtu.iron.storage.routing.api.route.storage.StorageRoute;
import com.xjtu.iron.storage.routing.api.route.StorageRouteMode;
import com.xjtu.iron.transaction.api.execution.TransactionExecutorResolver;
import java.util.Objects;

/** Tx-B 只消费已经绑定的路由，不重新 hash，不把业务 routeKey 当物理 dataSourceKey。
 * <p><b>流程阅读编号：I6.1：根据路由选择 Tx-B 执行器。</b>编号按 I（幂等）、R（路由）、D（数据访问）分组，不表示所有分支均依次执行。</p>
 * <ul>
 *     <li>1. 读取 I1 已绑定的 StorageRoute，要求直连模式；不再按业务 routeKey 计算分片。</li>
 *     <li>2. 用 dataSourceKey 找到对应 TransactionExecutor，委托 REQUIRED 协调器执行整段 work。</li>
 *     <li>3. 选择同名数据源还需装配一致的实际资源，才能让业务 SQL 与幂等 SQL 共享连接。</li>
 * </ul>
 */
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
