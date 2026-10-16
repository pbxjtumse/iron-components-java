package com.xjtu.iron.message.core.consume.idempotency;

import com.xjtu.iron.message.api.consume.context.ConsumeContext;
import com.xjtu.iron.message.api.consume.decision.ConsumeDecision;
import com.xjtu.iron.message.api.consume.definition.MessageIdempotencyOptions;
import com.xjtu.iron.message.api.model.MessageEnvelope;
import com.xjtu.iron.message.core.consume.ConsumeInvocation;

/**
 * 消息消费幂等执行器。
 */
public interface MessageIdempotencyExecutor {
    ConsumeDecision execute(MessageEnvelope<?> message, ConsumeContext context, MessageIdempotencyOptions options, ConsumeInvocation invocation);
}
