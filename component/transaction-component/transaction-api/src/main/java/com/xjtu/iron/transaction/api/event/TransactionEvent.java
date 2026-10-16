package com.xjtu.iron.transaction.api.event;

import com.xjtu.iron.transaction.api.status.TransactionOutcome;
import com.xjtu.iron.transaction.api.status.TransactionStage;

import java.time.Duration;

/**
 * 事务组件生命周期事件。
 *
 * <p>COMPLETED 只表示本次 {@code TransactionExecutor.execute(...)} 调用正常完成，
 * 不承诺“当前一定发生了独立物理 COMMIT”。例如 REQUIRED 复用外部事务时，
 * 最终物理提交仍然由外部事务边界决定。</p>
 *
 * <p>事件只携带事务元信息，不默认记录 SQL、请求体、密码、Token 等敏感内容。</p>
 */
public final class TransactionEvent {

    private final TransactionEventType type;
    private final String executionId;
    private final String transactionName;
    private final TransactionStage stage;
    private final TransactionOutcome outcome;
    private final Duration elapsed;
    private final Throwable failure;

    public TransactionEvent(
            TransactionEventType type,
            String executionId,
            String transactionName,
            TransactionStage stage,
            TransactionOutcome outcome,
            Duration elapsed,
            Throwable failure) {
        this.type = type;
        this.executionId = executionId;
        this.transactionName = transactionName;
        this.stage = stage;
        this.outcome = outcome;
        this.elapsed = elapsed;
        this.failure = failure;
    }

    public TransactionEventType type() { return type; }
    public String executionId() { return executionId; }
    public String transactionName() { return transactionName; }
    public TransactionStage stage() { return stage; }
    public TransactionOutcome outcome() { return outcome; }
    public Duration elapsed() { return elapsed; }
    public Throwable failure() { return failure; }
}
