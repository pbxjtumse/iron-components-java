package com.xjtu.iron.message.core.consume.strategy;

import com.xjtu.iron.message.api.consume.context.ConsumeContext;
import com.xjtu.iron.message.api.consume.decision.ConsumeDecision;
import com.xjtu.iron.message.api.consume.definition.ConsumerDefinition;
import com.xjtu.iron.message.api.consume.definition.MessageIdempotencyOptions;
import com.xjtu.iron.message.api.model.MessageEnvelope;
import com.xjtu.iron.message.core.consume.ConsumeInvocation;
import com.xjtu.iron.message.core.consume.idempotency.MessageIdempotencyExecutor;

import java.util.Objects;

/**
 * 默认消费幂等策略。
 *
 * <p>策略本身不实现幂等存储，只把 ConsumerDefinition 上的幂等配置交给
 * MessageIdempotencyExecutor 执行。这样单个消费者可以独立决定是否开启幂等。</p>
 */
public final class DefaultIdempotencyStrategy implements IdempotencyStrategy {

    private final MessageIdempotencyExecutor executor;

    public DefaultIdempotencyStrategy(MessageIdempotencyExecutor executor) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    @Override
    public ConsumeDecision execute(
            ConsumerDefinition<?> definition,
            MessageEnvelope<?> message,
            ConsumeContext context,
            ConsumeInvocation invocation) {
        MessageIdempotencyOptions options = definition == null
                ? MessageIdempotencyOptions.disabled()
                : definition.idempotencyOptions();
        return executor.execute(message, context, options, invocation);
    }
}
