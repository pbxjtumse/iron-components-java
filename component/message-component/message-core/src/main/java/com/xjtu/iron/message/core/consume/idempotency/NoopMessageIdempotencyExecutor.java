package com.xjtu.iron.message.core.consume.idempotency;

import com.xjtu.iron.message.api.consume.context.ConsumeContext;
import com.xjtu.iron.message.api.consume.decision.ConsumeDecision;
import com.xjtu.iron.message.api.consume.definition.MessageIdempotencyOptions;
import com.xjtu.iron.message.api.model.MessageEnvelope;
import com.xjtu.iron.message.core.consume.ConsumeInvocation;

/**
 * 关闭消费幂等时的默认执行器。
 */
public final class NoopMessageIdempotencyExecutor implements MessageIdempotencyExecutor {
    @Override
    public ConsumeDecision execute(MessageEnvelope<?> message, ConsumeContext context, MessageIdempotencyOptions options, ConsumeInvocation invocation) {
        return invocation.invoke();
    }
}
