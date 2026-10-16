package com.xjtu.iron.message.core.consume.strategy;

import com.xjtu.iron.message.api.consume.context.ConsumeContext;
import com.xjtu.iron.message.api.consume.decision.ConsumeDecision;
import com.xjtu.iron.message.api.consume.definition.ConsumerDefinition;
import com.xjtu.iron.message.core.consume.ConsumeInvocation;

/** 不启用消费事务时的空策略。 */
public final class NoopTransactionStrategy implements TransactionStrategy {

    @Override
    public ConsumeDecision execute(
            ConsumerDefinition<?> definition,
            ConsumeContext context,
            ConsumeInvocation invocation) {
        return invocation.invoke();
    }
}
