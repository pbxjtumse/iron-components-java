package com.xjtu.iron.transaction.core.context;

import com.xjtu.iron.transaction.api.context.TransactionContext;
import com.xjtu.iron.transaction.spi.provider.ProviderTransactionContext;

import java.util.Objects;

/**
 * API TransactionContext 与 ProviderTransactionContext 之间的默认桥接实现。
 */
public final class DefaultTransactionContext implements TransactionContext {

    private final String executionId;
    private final String transactionName;
    private final ProviderTransactionContext providerContext;

    public DefaultTransactionContext(
            String executionId,
            String transactionName,
            ProviderTransactionContext providerContext) {
        this.executionId = Objects.requireNonNull(executionId, "executionId");
        this.transactionName = Objects.requireNonNull(transactionName, "transactionName");
        this.providerContext = Objects.requireNonNull(providerContext, "providerContext");
    }

    @Override
    public String executionId() { return executionId; }

    @Override
    public String transactionName() { return transactionName; }

    @Override
    public boolean isRollbackOnly() {
        // 每次都读取 Provider 的实时状态，避免在 Context 创建时缓存一个已经过期的 rollback-only 值。
        return providerContext.isRollbackOnly();
    }

    @Override
    public void setRollbackOnly() {
        // 业务只表达“当前事务最终必须回滚”，具体是本事务回滚还是影响外层事务由 Provider/Spring 决定。
        providerContext.setRollbackOnly();
    }
}
