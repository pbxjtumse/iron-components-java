package com.xjtu.iron.message.core.consume.idempotency;

import com.xjtu.iron.message.api.consume.context.ConsumeContext;
import com.xjtu.iron.message.api.consume.definition.MessageIdempotencyOptions;
import com.xjtu.iron.message.api.model.MessageEnvelope;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultMessageIdempotencyKeyResolverTest {
    private final DefaultMessageIdempotencyKeyResolver resolver = new DefaultMessageIdempotencyKeyResolver();

    @Test
    void shouldResolveMessageIdKey() {
        MessageEnvelope<String> message = MessageEnvelope.builder("OrderPaid", "payload")
                .messageId("msg-001")
                .messageKey("order-10001")
                .build();
        ConsumeContext context = new ConsumeContext(
                "kafka",
                "order-topic",
                "order-paid-consumer-group",
                "order-topic-0@6",
                1,
                Instant.now(),
                Map.of());
        String key = resolver.resolve(message, context, MessageIdempotencyOptions.messageId());
        assertEquals("kafka:order-topic:order-paid-consumer-group:msg-001", key);
    }
}
