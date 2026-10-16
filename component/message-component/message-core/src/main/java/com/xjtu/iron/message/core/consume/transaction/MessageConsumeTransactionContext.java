package com.xjtu.iron.message.core.consume.transaction;

import com.xjtu.iron.message.api.consume.context.ConsumeContext;
import com.xjtu.iron.message.api.consume.definition.ConsumerDefinition;
import com.xjtu.iron.message.api.consume.definition.MessageConsumeTransactionOptions;

import java.util.Objects;

/**
 * 消费事务上下文。
 *
 * <p>V4 后事务上下文不再携带 MessageIdempotencyContext，事务只面向一次消费执行，
 * 幂等 acquire、success、failed 的状态推进由 MessageIdempotencyExecutor 自己处理。</p>
 */
public final class MessageConsumeTransactionContext {

    private final ConsumerDefinition<?> definition;
    private final ConsumeContext consumeContext;
    private final MessageConsumeTransactionOptions options;

    public MessageConsumeTransactionContext(
            ConsumerDefinition<?> definition,
            ConsumeContext consumeContext,
            MessageConsumeTransactionOptions options) {
        this.definition = Objects.requireNonNull(definition, "definition must not be null");
        this.consumeContext = Objects.requireNonNull(consumeContext, "consumeContext must not be null");
        this.options = options == null ? MessageConsumeTransactionOptions.disabled() : options;
    }

    public ConsumerDefinition<?> definition() {
        return definition;
    }

    public ConsumeContext consumeContext() {
        return consumeContext;
    }

    public MessageConsumeTransactionOptions options() {
        return options;
    }
}
