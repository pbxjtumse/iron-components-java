package com.xjtu.iron.transaction.api.status;

/**
 * 事务执行阶段，用于错误定位、事件和可观测性。
 */
public enum TransactionStage {
    VALIDATE,
    RESOLVE,
    BEGIN,
    EXECUTE,
    COMMIT,
    ROLLBACK,
    COMPLETION
}
