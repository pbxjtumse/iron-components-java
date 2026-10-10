package com.xjtu.iron.idempotent.core.transaction;

/**
 * 幂等组件对“业务事务边界”的最小依赖。
 *
 * <p>Core 不直接依赖 Spring Transaction，也不直接依赖 transaction-component。
 * 真正的 transaction-component 适配放在 {@code idempotent-integration-transaction} 中。</p>
 *
 * <p>本接口只负责 Tx-B：</p>
 * <pre>
 * REQUIRED
 *   business callback
 *   + markSuccess(ownerToken, version)
 * </pre>
 *
 * <p>Tx-A（tryAcquire/tryRecover）和 Tx-C（markFailed）的 REQUIRES_NEW 由 JDBC Provider 的
 * {@code MyBatisAccess} 负责，因此三段事务职责不会混在一个类里。</p>
 */
public interface IdempotencyTransactionCoordinator {

    /**
     * 在 REQUIRED 本地事务中执行业务与 SUCCESS 状态提交。
     *
     * @param transactionName 低基数逻辑事务名称，不应包含 idempotencyKey 等高基数值
     * @param routeKey 分片路由元数据；当前 transaction-component 一期是单 TransactionManager，
     *                 暂不消费该字段，但保留它避免未来路由事务再次修改 Core 主流程
     * @param work 需要处于同一事务中的工作
     */
    <T> T executeRequired(String transactionName, String routeKey, IdempotencyTransactionalWork<T> work) throws Exception;
}
