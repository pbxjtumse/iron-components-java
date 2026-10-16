package com.xjtu.iron.message.core.consume;

import com.xjtu.iron.message.api.consume.context.ConsumeContext;
import com.xjtu.iron.message.api.consume.definition.ConsumerDefinition;
import com.xjtu.iron.message.api.model.MessageEnvelope;
import com.xjtu.iron.message.spi.ProviderDestination;
import com.xjtu.iron.message.spi.ProviderInboundMessage;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 根据解码后的消息、Provider 入站消息和消费者定义创建业务可见的消费上下文。
 */
public final class DefaultConsumeContextFactory {
    public ConsumeContext create(
            ConsumerDefinition<?> definition,
            ProviderDestination providerDestination,
            ProviderInboundMessage inbound,
            MessageEnvelope<?> envelope) {
        Map<String, String> attributes = new LinkedHashMap<>(inbound.providerMetadata());
        return new ConsumeContext(
                providerDestination.providerName(),
                providerDestination.physicalName(),
                definition.destination(),
                definition.consumerGroup(),
                inbound.providerMessageId(),
                envelope.messageId(),
                envelope.messageKey(),
                envelope.messageType(),
                inbound.deliveryAttempt(),
                inbound.receivedAt(),
                Instant.now(),
                definition.reliabilityMode(),
                definition.idempotencyOptions().mode(),
                definition.idempotencyOptions().scene(),
                null,
                envelope.headers().asMap(),
                attributes);
    }
}
