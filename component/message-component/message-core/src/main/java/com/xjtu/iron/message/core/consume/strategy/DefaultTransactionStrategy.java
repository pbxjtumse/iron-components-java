package com.xjtu.iron.message.core.consume.strategy;

import com.xjtu.iron.message.api.consume.context.ConsumeContext;
import com.xjtu.iron.message.api.consume.decision.ConsumeDecision;
import com.xjtu.iron.message.api.consume.definition.ConsumerDefinition;
import com.xjtu.iron.message.api.consume.definition.MessageConsumeTransactionOptions;
import com.xjtu.iron.message.core.consume.ConsumeInvocation;
import com.xjtu.iron.message.core.consume.transaction.MessageConsumeTransactionContext;
import com.xjtu.iron.message.core.consume.transaction.MessageConsumeTransactionExecutor;

import java.util.Objects;

/**
 * 默认消费事务策略。
 *
 * <p>V4 后事务由 MessageConsumeExecutor 明确编排，幂等执行器不再直接感知事务。
 * 该策略只负责根据 ConsumerDefinition 的事务配置决定是否调用底层事务执行器。</p>
 *
 * <p>注意：MessageHandlerInvoker 会把业务异常分类成 ConsumeDecision。为了避免
 * Handler 已经抛异常但异常被分类后导致事务误提交，本策略会把 RETRY 和 DEAD_LETTER
 * 转换成内部运行时异常，交给真实事务执行器触发 rollback，然后再恢复成原始消费决策。</p>
 */
public final class DefaultTransactionStrategy implements TransactionStrategy {

    private final MessageConsumeTransactionExecutor transactionExecutor;

    public DefaultTransactionStrategy(MessageConsumeTransactionExecutor transactionExecutor) {
        this.transactionExecutor = Objects.requireNonNull(transactionExecutor, "transactionExecutor must not be null");
    }

    @Override
    public ConsumeDecision execute(
            ConsumerDefinition<?> definition,
            ConsumeContext context,
            ConsumeInvocation invocation) {
        Objects.requireNonNull(definition, "definition must not be null");
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(invocation, "invocation must not be null");

        MessageConsumeTransactionOptions options = definition.transactionOptions();
        if (options == null || !options.enabled()) {
            return invocation.invoke();
        }

        MessageConsumeTransactionContext transactionContext =
                new MessageConsumeTransactionContext(definition, context, options);
        try {
            return transactionExecutor.execute(transactionContext, () -> {
                ConsumeDecision decision = invocation.invoke();
                if (shouldRollback(decision)) {
                    throw new RollbackOnlyConsumeException(decision == null ? ConsumeDecision.RETRY : decision);
                }
                return decision;
            });
        } catch (RollbackOnlyConsumeException exception) {
            return exception.decision();
        }
    }

    private static boolean shouldRollback(ConsumeDecision decision) {
        return decision == null
                || decision == ConsumeDecision.RETRY
                || decision == ConsumeDecision.DEAD_LETTER;
    }

    @SuppressWarnings("serial")
    private static final class RollbackOnlyConsumeException extends RuntimeException {

        private final ConsumeDecision decision;

        private RollbackOnlyConsumeException(ConsumeDecision decision) {
            super("consume transaction rollback requested: " + decision);
            this.decision = decision;
        }

        private ConsumeDecision decision() {
            return decision;
        }
    }
}
