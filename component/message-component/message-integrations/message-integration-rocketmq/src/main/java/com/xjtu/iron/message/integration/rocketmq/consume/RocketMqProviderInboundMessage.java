package com.xjtu.iron.message.integration.rocketmq.consume;

import com.xjtu.iron.message.spi.ProviderInboundMessage;

import java.time.Instant;
import java.util.Map;

/** RocketMQ Provider 入站消息实现。 */
public final class RocketMqProviderInboundMessage extends ProviderInboundMessage {
    public RocketMqProviderInboundMessage(
            String physicalDestination,
            String providerMessageId,
            String messageKey,
            Map<String, String> headers,
            byte[] body,
            Instant receivedAt,
            RocketMqMessagePosition position,
            Map<String, String> metadata) {
        super("rocketmq", physicalDestination, providerMessageId, messageKey, headers, body, receivedAt, 1, position, metadata);
    }
}
