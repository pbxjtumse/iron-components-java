package com.xjtu.iron.message.core.consume;

import com.xjtu.iron.message.api.consume.context.ConsumeContext;
import com.xjtu.iron.message.api.consume.decision.ConsumeDecision;
import com.xjtu.iron.message.api.consume.definition.ConsumerDefinition;
import com.xjtu.iron.message.api.consume.handler.MessageHandler;
import com.xjtu.iron.message.api.model.MessageEnvelope;
import com.xjtu.iron.message.core.consume.handler.MessageHandlerInvoker;
import com.xjtu.iron.message.core.consume.strategy.IdempotencyStrategy;
import com.xjtu.iron.message.core.consume.strategy.TransactionStrategy;

import java.util.Objects;

/**
 * 消费执行器。
 *
 * <p>它是消费侧对应 MessageSendExecutor 的核心执行入口。该类只固定消费主流程：
 * 幂等策略 -> 事务策略 -> 业务 Handler 调用。幂等和事务是否真正生效，分别由
 * IdempotencyStrategy 和 TransactionStrategy 决定。</p>
 */
public final class MessageConsumeExecutor {

    private final IdempotencyStrategy idempotencyStrategy;
    private final TransactionStrategy transactionStrategy;
    private final MessageHandlerInvoker handlerInvoker;

    public MessageConsumeExecutor(
            IdempotencyStrategy idempotencyStrategy,
            TransactionStrategy transactionStrategy,
            MessageHandlerInvoker handlerInvoker) {
        this.idempotencyStrategy = Objects.requireNonNull(idempotencyStrategy, "idempotencyStrategy must not be null");
        this.transactionStrategy = Objects.requireNonNull(transactionStrategy, "transactionStrategy must not be null");
        this.handlerInvoker = Objects.requireNonNull(handlerInvoker, "handlerInvoker must not be null");
    }

    public <T> ConsumeDecision execute(
            ConsumerDefinition<T> definition,
            MessageEnvelope<T> message,
            ConsumeContext context,
            MessageHandler<T> handler) {
        Objects.requireNonNull(definition, "definition must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(handler, "handler must not be null");
        return idempotencyStrategy.execute(definition, message, context,
                () -> transactionStrategy.execute(definition, context,
                        () -> handlerInvoker.invoke(message, context, handler)));
    }
}
