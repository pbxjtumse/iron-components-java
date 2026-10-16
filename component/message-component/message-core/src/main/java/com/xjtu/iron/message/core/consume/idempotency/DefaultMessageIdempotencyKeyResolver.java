package com.xjtu.iron.message.core.consume.idempotency;

import com.xjtu.iron.message.api.consume.context.ConsumeContext;
import com.xjtu.iron.message.api.consume.definition.MessageIdempotencyMode;
import com.xjtu.iron.message.api.consume.definition.MessageIdempotencyOptions;
import com.xjtu.iron.message.api.model.MessageEnvelope;

/**
 * 默认消费幂等 key 解析器。
 */
public final class DefaultMessageIdempotencyKeyResolver implements MessageIdempotencyKeyResolver {
    @Override
    public String resolve(MessageEnvelope<?> message, ConsumeContext context, MessageIdempotencyOptions options) {
        MessageIdempotencyMode mode = options.mode();
        if (mode == MessageIdempotencyMode.BUSINESS_KEY) {
            return businessKey(message);
        }
        if (mode == MessageIdempotencyMode.CUSTOM) {
            throw new IllegalStateException("CUSTOM idempotency mode requires a custom MessageIdempotencyKeyResolver");
        }
        return messageIdKey(message, context);
    }

    private static String messageIdKey(MessageEnvelope<?> message, ConsumeContext context) {
        return requireText(context.providerName(), "providerName") + ":"
                + requireText(context.physicalDestination(), "physicalDestination") + ":"
                + requireText(context.consumerGroup(), "consumerGroup") + ":"
                + requireText(message.messageId(), "messageId");
    }

    private static String businessKey(MessageEnvelope<?> message) {
        return requireText(message.messageType(), "messageType") + ":" + requireText(message.messageKey(), "messageKey");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank when resolving idempotency key");
        }
        return value.trim();
    }
}
