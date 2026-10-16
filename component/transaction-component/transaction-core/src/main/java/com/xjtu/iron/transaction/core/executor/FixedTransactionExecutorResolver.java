package com.xjtu.iron.transaction.core.executor;

import com.xjtu.iron.transaction.api.execution.TransactionExecutor;
import com.xjtu.iron.transaction.api.execution.TransactionExecutorResolver;
import java.util.Map;

/** 单资源仍校验 key，避免固定执行器掩盖路由错误。 */
public final class FixedTransactionExecutorResolver implements TransactionExecutorResolver {
    private final RoutingTransactionExecutorResolver delegate;

    public FixedTransactionExecutorResolver(String resourceKey, TransactionExecutor executor) {
        this.delegate = new RoutingTransactionExecutorResolver(Map.of(resourceKey, executor));
    }

    @Override
    public TransactionExecutor resolve(String resourceKey) {
        return delegate.resolve(resourceKey);
    }
}
