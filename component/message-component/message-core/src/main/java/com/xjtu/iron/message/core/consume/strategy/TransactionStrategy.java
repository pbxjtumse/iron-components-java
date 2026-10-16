package com.xjtu.iron.message.core.consume.strategy;

import com.xjtu.iron.message.api.consume.context.ConsumeContext;
import com.xjtu.iron.message.api.consume.decision.ConsumeDecision;
import com.xjtu.iron.message.api.consume.definition.ConsumerDefinition;
import com.xjtu.iron.message.core.consume.ConsumeInvocation;

/**
 * 消费事务策略。
 *
 * <p>MessageConsumeExecutor 固定“业务 Handler 可以被事务策略包裹”的流程，
 * 具体是否开启事务、如何接入 Spring 事务或自研事务模板，由该策略实现决定。</p>
 */
public interface TransactionStrategy {

    ConsumeDecision execute(
            ConsumerDefinition<?> definition,
            ConsumeContext context,
            ConsumeInvocation invocation);
}
