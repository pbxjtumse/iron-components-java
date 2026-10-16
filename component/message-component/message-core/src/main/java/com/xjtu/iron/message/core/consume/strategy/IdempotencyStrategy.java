package com.xjtu.iron.message.core.consume.strategy;

import com.xjtu.iron.message.api.consume.context.ConsumeContext;
import com.xjtu.iron.message.api.consume.decision.ConsumeDecision;
import com.xjtu.iron.message.api.consume.definition.ConsumerDefinition;
import com.xjtu.iron.message.api.model.MessageEnvelope;
import com.xjtu.iron.message.core.consume.ConsumeInvocation;

/**
 * 消费幂等策略。
 *
 * <p>MessageConsumeExecutor 只固定“消费需要经过幂等步骤”这个流程，
 * 具体是否启用幂等、使用哪一种幂等配置，由该策略根据 ConsumerDefinition 决定。</p>
 */
public interface IdempotencyStrategy {

    ConsumeDecision execute(ConsumerDefinition<?> definition, MessageEnvelope<?> message, ConsumeContext context, ConsumeInvocation invocation);
}
