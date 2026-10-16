package com.xjtu.iron.message.integration.pulsar.consume;

import com.xjtu.iron.message.spi.ProviderInboundMessage;

import java.time.Instant;
import java.util.Map;

/** Pulsar Provider 入站消息实现。 */
public final class PulsarProviderInboundMessage extends ProviderInboundMessage {
    public PulsarProviderInboundMessage(
            String physicalDestination,
            String providerMessageId,
            String messageKey,
            Map<String, String> headers,
            byte[] body,
            Instant receivedAt,
            PulsarMessagePosition position,
            Map<String, String> metadata) {
        super("pulsar", physicalDestination, providerMessageId, messageKey, headers, body, receivedAt, 1, position, metadata);
    }
}
