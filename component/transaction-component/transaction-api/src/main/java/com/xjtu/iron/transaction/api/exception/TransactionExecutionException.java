package com.xjtu.iron.transaction.api.exception;

import com.xjtu.iron.transaction.api.status.TransactionOutcome;
import com.xjtu.iron.transaction.api.status.TransactionStage;

/**
 * 事务基础设施执行异常。
 *
 * <p>业务 callback 自己抛出的 RuntimeException/Error 不会被统一包装；
 * 本异常主要表示 BEGIN、COMMIT、ROLLBACK 等事务基础设施阶段失败。</p>
 */
public class TransactionExecutionException extends RuntimeException {

    private final String executionId;
    private final String transactionName;
    private final TransactionStage stage;
    private final TransactionOutcome outcome;

    public TransactionExecutionException(
            String message,
            String executionId,
            String transactionName,
            TransactionStage stage,
            TransactionOutcome outcome,
            Throwable cause) {
        super(message, cause);
        this.executionId = executionId;
        this.transactionName = transactionName;
        this.stage = stage;
        this.outcome = outcome;
    }

    public String executionId() { return executionId; }
    public String transactionName() { return transactionName; }
    public TransactionStage stage() { return stage; }
    public TransactionOutcome outcome() { return outcome; }
}
