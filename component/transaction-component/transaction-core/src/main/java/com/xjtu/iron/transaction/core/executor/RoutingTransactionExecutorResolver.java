package com.xjtu.iron.transaction.core.executor;

import com.xjtu.iron.transaction.api.execution.TransactionExecutor;
import com.xjtu.iron.transaction.api.execution.TransactionExecutorResolver;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 严格资源映射：未知或空 key 必须失败，禁止悄悄落到默认库。 */
public final class RoutingTransactionExecutorResolver implements TransactionExecutorResolver {
    private final Map<String, TransactionExecutor> executors;

    public RoutingTransactionExecutorResolver(Map<String, ? extends TransactionExecutor> executors) {
        Objects.requireNonNull(executors, "executors");
        if (executors.isEmpty()) throw new IllegalArgumentException("executors must not be empty");
        Map<String, TransactionExecutor> copy = new LinkedHashMap<>();
        executors.forEach((key, executor) -> {
            if (key == null || key.isBlank()) throw new IllegalArgumentException("resourceKey must not be blank");
            if (copy.put(key.trim(), Objects.requireNonNull(executor, "executor")) != null) {
                throw new IllegalArgumentException("duplicate resourceKey=" + key.trim());
            }
        });
        this.executors = Map.copyOf(copy);
    }

    @Override
    public TransactionExecutor resolve(String resourceKey) {
        TransactionExecutor executor = resourceKey == null ? null : executors.get(resourceKey.trim());
        if (executor == null) throw new IllegalArgumentException("TransactionExecutor not found for resourceKey=" + resourceKey);
        return executor;
    }
}
