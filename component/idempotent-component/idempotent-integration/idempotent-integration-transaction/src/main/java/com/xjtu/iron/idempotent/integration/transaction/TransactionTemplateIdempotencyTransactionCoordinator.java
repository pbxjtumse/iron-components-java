package com.xjtu.iron.idempotent.integration.transaction;

import com.xjtu.iron.idempotent.core.transaction.IdempotencyTransactionCoordinator;
import com.xjtu.iron.idempotent.core.transaction.IdempotencyTransactionException;
import com.xjtu.iron.idempotent.core.transaction.IdempotencyTransactionOutcome;
import com.xjtu.iron.idempotent.core.transaction.IdempotencyTransactionalWork;
import com.xjtu.iron.transaction.api.definition.TransactionOptions;
import com.xjtu.iron.transaction.api.definition.TransactionPropagation;
import com.xjtu.iron.transaction.api.exception.TransactionExecutionException;
import com.xjtu.iron.transaction.api.execution.TransactionExecutor;
import com.xjtu.iron.transaction.api.status.TransactionOutcome;

import java.util.Objects;

/**
 * 使用 transaction-component 的 {@link TransactionExecutor} 实现幂等 Tx-B。
 *
 * <p>Tx-B 固定使用 REQUIRED，因为它的职责不是“单独提交幂等状态”，而是让：</p>
 * <pre>
 * Business callback
 * + ResultPolicy.capture
 * + markSuccess(ownerToken, version)
 * </pre>
 * <p>与调用方真实业务事务处于同一个本地事务边界。外层已有事务时加入外层；没有事务时才创建事务。
 * 如果这里强制 REQUIRES_NEW，可能出现幂等 SUCCESS 已提交，但调用方外层业务随后 rollback 的错误结果。</p>
 */
public final class TransactionTemplateIdempotencyTransactionCoordinator implements IdempotencyTransactionCoordinator {

    private final TransactionExecutor transactionExecutor;

    public TransactionTemplateIdempotencyTransactionCoordinator(TransactionExecutor transactionExecutor) {
        this.transactionExecutor = Objects.requireNonNull(transactionExecutor, "transactionExecutor must not be null");
    }

    /**
     * 执行 Tx-B REQUIRED。
     *
     * <p>Core 传入的 work 已经包含 Business + capture + markSuccess。任何一步抛异常都必须让当前事务回滚。
     * transactionName 只使用稳定业务维度，不拼 idempotencyKey，避免日志和指标出现高基数。</p>
     */
    @Override
    public <T> T executeRequired(String transactionName, String routeKey, IdempotencyTransactionalWork<T> work) throws Exception {
        Objects.requireNonNull(work, "work must not be null");

        TransactionOptions options = TransactionOptions.builder()
                .name(normalizeName(transactionName))
                .propagation(TransactionPropagation.REQUIRED)
                .build();

        try {
            return transactionExecutor.execute(options, context -> {
                try {
                    return work.execute();
                } catch (RuntimeException | Error unchecked) {
                    // 运行时业务异常保持原样抛出，让 TransactionExecutor 按真实异常触发 rollback。
                    throw unchecked;
                } catch (Exception checked) {
                    // transaction-api callback 不声明 checked exception，只在跨接口边界时临时包装；
                    // 事务回滚完成回到外层后再还原原始 checked exception。
                    throw new CheckedWorkRuntimeException(checked);
                }
            });
        } catch (CheckedWorkRuntimeException checked) {
            throw checked.original;
        } catch (TransactionExecutionException infrastructureFailure) {
            // BEGIN / COMMIT / ROLLBACK 等事务基础设施异常必须与业务异常分开表达。
            throw new IdempotencyTransactionException(
                    "idempotency business transaction failed at stage " + infrastructureFailure.stage(),
                    infrastructureFailure.stage().name(), mapOutcome(infrastructureFailure.outcome()), infrastructureFailure);
        }
    }

    private String normalizeName(String transactionName) {
        return transactionName == null || transactionName.isBlank() ? "idempotency-business" : transactionName;
    }

    /**
     * 把 transaction-component 的结果映射成幂等组件真正关心的事务语义。
     * COMMIT_UNKNOWN 必须单独保留，Core 遇到它不能直接 markFailed。
     */
    private IdempotencyTransactionOutcome mapOutcome(TransactionOutcome outcome) {
        if (outcome == null) {
            return IdempotencyTransactionOutcome.FAILED;
        }
        return switch (outcome) {
            case ROLLED_BACK -> IdempotencyTransactionOutcome.ROLLED_BACK;
            case COMMIT_UNKNOWN -> IdempotencyTransactionOutcome.COMMIT_UNKNOWN;
            case FAILED -> IdempotencyTransactionOutcome.FAILED;
        };
    }

    /** 只用于跨过 transaction-api 不声明 checked exception 的 callback 边界，不承载领域语义。 */
    private static final class CheckedWorkRuntimeException extends RuntimeException {
        private final Exception original;

        private CheckedWorkRuntimeException(Exception original) {
            super(original);
            this.original = original;
        }
    }
}
