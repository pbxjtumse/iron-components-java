package com.xjtu.iron.transaction.spi.exception;

import com.xjtu.iron.transaction.api.status.TransactionOutcome;
import com.xjtu.iron.transaction.api.status.TransactionStage;

/**
 * Provider 到 core 之间的事务基础设施异常。
 */
public class ProviderTransactionException extends RuntimeException {

    private final TransactionStage stage;
    private final TransactionOutcome outcome;

    public ProviderTransactionException(
            String message,
            TransactionStage stage,
            TransactionOutcome outcome,
            Throwable cause) {
        super(message, cause);
        this.stage = stage;
        this.outcome = outcome;
    }

    public TransactionStage stage() { return stage; }
    public TransactionOutcome outcome() { return outcome; }
}
