package com.xjtu.iron.message.spring.boot.autoconfigure.consume;

import com.xjtu.iron.message.api.consume.definition.ConsumerDefinition;
import com.xjtu.iron.message.api.consume.definition.ConsumerReliabilityMode;
import com.xjtu.iron.message.spi.MessageCapability;
import com.xjtu.iron.message.spi.MessageProvider;

/**
 * 启动期消费者定义校验器。
 */
public final class MessageConsumerDefinitionValidator {
    public void validate(ConsumerDefinition<?> definition, MessageProvider provider) {
        if (definition == null) {
            throw new IllegalArgumentException("consumer definition must not be null");
        }
        if (provider == null) {
            throw new IllegalStateException("message provider must not be null for consumer " + definition.consumerId());
        }
        if (requiresReliableAck(definition.reliabilityMode()) && !hasReliableAckCapability(provider)) {
            throw new IllegalStateException("provider does not support reliable consume acknowledgement: " + provider.name());
        }
        if (definition.reliabilityMode() == ConsumerReliabilityMode.EFFECTIVELY_ONCE
                && !definition.idempotencyOptions().enabled()) {
            throw new IllegalStateException("EFFECTIVELY_ONCE requires idempotency.enabled=true: " + definition.consumerId());
        }
        if (definition.reliabilityMode() == ConsumerReliabilityMode.EFFECTIVELY_ONCE
                && definition.transactionOptions().required()
                && !definition.transactionOptions().enabled()) {
            throw new IllegalStateException("EFFECTIVELY_ONCE requires transaction when transaction.required=true: " + definition.consumerId());
        }
    }

    private static boolean requiresReliableAck(ConsumerReliabilityMode mode) {
        return mode == ConsumerReliabilityMode.AT_LEAST_ONCE || mode == ConsumerReliabilityMode.EFFECTIVELY_ONCE;
    }

    private static boolean hasReliableAckCapability(MessageProvider provider) {
        return provider.capabilities().contains(MessageCapability.OFFSET_COMMIT)
                || provider.capabilities().contains(MessageCapability.MANUAL_ACK)
                || provider.capabilities().contains(MessageCapability.RETURN_CONSUME_STATUS);
    }
}
