package com.xjtu.iron.message.integration.kafka.consume;

import com.xjtu.iron.message.spi.ProviderInboundMessage;

import java.time.Instant;
import java.util.Map;

/** Kafka Provider 入站消息实现。 */
public final class KafkaProviderInboundMessage extends ProviderInboundMessage {
    public KafkaProviderInboundMessage(
            String physicalDestination,
            String providerMessageId,
            String messageKey,
            Map<String, String> headers,
            byte[] body,
            Instant receivedAt,
            KafkaMessagePosition position,
            Map<String, String> metadata) {
        super("kafka", physicalDestination, providerMessageId, messageKey, headers, body, receivedAt, 1, position, metadata);
    }
}
