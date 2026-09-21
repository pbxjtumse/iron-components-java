package com.xjtu.iron.transaction.api.execution;

/** 按物理资源标识选择本地事务执行器；不计算分片，也不协调跨库事务。 */
@FunctionalInterface
public interface TransactionExecutorResolver {
    TransactionExecutor resolve(String resourceKey);
}
