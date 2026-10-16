package com.xjtu.iron.message.api.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
/**
 * message-component 对外暴露的统一消息信封。
 *
 * <p>业务真正关心的是 payload，但一个可治理的消息组件还必须携带 metadata、context 和 headers。
 * metadata 表达消息自身身份，例如 messageId、messageType、messageKey、schemaVersion；
 * context 表达调用链路，例如 source、correlationId、causationId、tenantId；headers 则用于扩展治理字段。</p>
 *
 * <p>所有 Provider 都应该围绕 {@code MessageEnvelope} 进行适配，而不是让业务代码直接接触 Kafka Record、
 * Pulsar Message 或 RocketMQ Message。这样后续可靠发送、消费幂等、Outbox 和观测指标才能复用统一模型。</p>
 */
public final class MessageEnvelope<T> {

    private final MessageMetadata metadata;
    private final MessageContext context;
    private final MessageHeaders headers;
    private final T payload;

    private MessageEnvelope(Builder<T> builder) {
        this.metadata = new MessageMetadata(
                builder.messageId,
                builder.messageType,
                builder.schemaVersion,
                builder.messageKey,
                builder.occurredAt,
                builder.createdAt);
        this.context = builder.context == null ? MessageContext.empty() : builder.context;
        this.headers = builder.headers == null ? MessageHeaders.empty() : builder.headers;
        this.payload = Objects.requireNonNull(builder.payload, "payload must not be null");
    }

    /** 创建只要求消息类型和业务数据的 Builder。 */
    public static <T> Builder<T> builder(String messageType, T payload) {
        return new Builder<>(messageType, payload);
    }

    /** 创建最小消息信封。 */
    public static <T> MessageEnvelope<T> of(String messageType, T payload) {
        return builder(messageType, payload).build();
    }

    /** 使用三个结构化对象和 payload 直接创建完整信封。 */
    public static <T> MessageEnvelope<T> of(
            MessageMetadata metadata,
            MessageContext context,
            MessageHeaders headers,
            T payload) {
        Objects.requireNonNull(metadata, "metadata must not be null");
        return builder(metadata.messageType(), payload)
                .messageId(metadata.messageId())
                .schemaVersion(metadata.schemaVersion())
                .messageKey(metadata.messageKey())
                .occurredAt(metadata.occurredAt())
                .createdAt(metadata.createdAt())
                .context(context)
                .headers(headers)
                .build();
    }

    /** 返回复制当前字段的 Builder。 */
    public Builder<T> toBuilder() {
        return new Builder<>(metadata.messageType(), payload)
                .messageId(metadata.messageId())
                .schemaVersion(metadata.schemaVersion())
                .messageKey(metadata.messageKey())
                .occurredAt(metadata.occurredAt())
                .createdAt(metadata.createdAt())
                .context(context)
                .headers(headers);
    }

    public MessageMetadata metadata() {
        return metadata;
    }

    public MessageContext context() {
        return context;
    }

    public MessageHeaders headers() {
        return headers;
    }

    public T payload() {
        return payload;
    }

    /** 高频诊断便捷方法。 */
    public String messageId() {
        return metadata.messageId();
    }

    /** 高频契约便捷方法。 */
    public String messageType() {
        return metadata.messageType();
    }

    /** 高频路由便捷方法。 */
    public String messageKey() {
        return metadata.messageKey();
    }

    /** 用于构造不可变消息信封。 */
    public static final class Builder<T> {

        private String messageId;
        private final String messageType;
        private String schemaVersion;
        private String messageKey;
        private Instant occurredAt;
        private Instant createdAt;
        private MessageContext context;
        private MessageHeaders headers = MessageHeaders.empty();
        private final T payload;

        private Builder(String messageType, T payload) {
            this.messageType = messageType;
            this.payload = payload;
        }

        public Builder<T> messageId(String messageId) {
            this.messageId = messageId;
            return this;
        }

        public Builder<T> schemaVersion(String schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }

        public Builder<T> messageKey(String messageKey) {
            this.messageKey = messageKey;
            return this;
        }

        public Builder<T> occurredAt(Instant occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        public Builder<T> createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder<T> context(MessageContext context) {
            this.context = context;
            return this;
        }

        public Builder<T> headers(MessageHeaders headers) {
            this.headers = headers == null ? MessageHeaders.empty() : headers;
            return this;
        }

        public Builder<T> headers(Map<String, String> headers) {
            this.headers = MessageHeaders.of(headers);
            return this;
        }

        public Builder<T> header(String name, String value) {
            this.headers = this.headers.with(name, value);
            return this;
        }

        public MessageEnvelope<T> build() {
            return new MessageEnvelope<>(this);
        }
    }
}
