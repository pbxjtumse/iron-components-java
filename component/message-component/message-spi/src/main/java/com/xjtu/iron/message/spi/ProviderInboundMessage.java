package com.xjtu.iron.message.spi;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Provider 交给 core 的原始入站消息。
 * 为什么需要 ？ 把不同 MQ 的原始消息，转换成统一内部消息
 * <p>该类可以作为公共基类使用。Kafka、Pulsar、RocketMQ 等 Provider 可以继承它，保存自己的原生位置对象，
 * 但 core 只依赖这里的公共字段。</p>
 */
public class ProviderInboundMessage {
    private final String providerName;
    private final String physicalDestination;
    /**
     * 注意 这里是中间件返回的消息id
     */
    private final String providerMessageId;
    private final String messageKey;
    private final Map<String, String> headers;
    private final byte[] body;
    private final Instant receivedAt;
    private final int deliveryAttempt;
    private final ProviderMessagePosition position;
    private final Map<String, String> providerMetadata;

    public ProviderInboundMessage(
            String providerMessageId,
            String messageKey,
            Map<String, String> headers,
            byte[] body,
            Instant receivedAt,
            Map<String, String> providerMetadata) {
        this(
                null,
                null,
                providerMessageId,
                messageKey,
                headers,
                body,
                receivedAt,
                1,
                null,
                providerMetadata);
    }

    public ProviderInboundMessage(
            String providerName,
            String physicalDestination,
            String providerMessageId,
            String messageKey,
            Map<String, String> headers,
            byte[] body,
            Instant receivedAt,
            int deliveryAttempt,
            ProviderMessagePosition position,
            Map<String, String> providerMetadata) {
        this.providerName = normalize(providerName);
        this.physicalDestination = normalize(physicalDestination);
        this.providerMessageId = normalize(providerMessageId);
        this.messageKey = normalize(messageKey);
        this.headers = immutable(headers);
        this.body = Objects.requireNonNull(body, "body must not be null").clone();
        this.receivedAt = receivedAt == null ? Instant.now() : receivedAt;
        this.deliveryAttempt = Math.max(1, deliveryAttempt);
        this.position = position;
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.putAll(immutable(providerMetadata));
        if (position != null) {
            metadata.putAll(position.attributes());
        }
        this.providerMetadata = Collections.unmodifiableMap(metadata);
    }

    public String providerName() { return providerName; }
    public String physicalDestination() { return physicalDestination; }
    public String providerMessageId() { return providerMessageId; }
    public String messageKey() { return messageKey; }
    public Map<String, String> headers() { return headers; }
    public byte[] body() { return body.clone(); }
    public Instant receivedAt() { return receivedAt; }
    public int deliveryAttempt() { return deliveryAttempt; }
    public ProviderMessagePosition position() { return position; }
    public Map<String, String> providerMetadata() { return providerMetadata; }

    private static Map<String, String> immutable(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
