package com.xjtu.iron.message.core.enrich;

import com.xjtu.iron.foundation.id.api.StringIdGenerator;
import com.xjtu.iron.message.api.model.MessageContext;
import com.xjtu.iron.message.api.model.MessageEnvelope;
import com.xjtu.iron.message.api.model.MessageMetadata;
import com.xjtu.iron.message.core.MessageComponentOptions;
import com.xjtu.iron.message.core.context.CurrentMessage;
import com.xjtu.iron.message.core.context.MessageContextAccessor;

import java.time.Instant;
import java.util.Objects;
/**
 * 发送前的消息信封补齐器。
 *
 * <p>业务方构造 {@code MessageEnvelope} 时，通常只关心 payload、messageType、messageKey 等业务字段。
 * 组件在真正发送前需要补齐 messageId、createdAt、occurredAt、source、correlationId、causationId 等公共信息。
 * 这些补齐动作集中放在本类，避免散落在 MessageTemplate 或各 Provider 中。</p>
 *
 * <p>messageId 的生成直接复用 foundation-component 的 {@code StringIdGenerator}。message-core 不再维护
 * 自己的消息 ID 生成抽象，避免基础能力重复建设。</p>
 */
public final class MessageEnvelopeEnricher {

    private final MessageComponentOptions options;
    private final StringIdGenerator messageIdGenerator;
    private final MessageContextAccessor contextAccessor;

    public MessageEnvelopeEnricher(
            MessageComponentOptions options,
            StringIdGenerator messageIdGenerator,
            MessageContextAccessor contextAccessor) {
        this.options = Objects.requireNonNull(options, "options must not be null");
        this.messageIdGenerator = Objects.requireNonNull(messageIdGenerator, "messageIdGenerator must not be null");
        this.contextAccessor = Objects.requireNonNull(contextAccessor, "contextAccessor must not be null");
    }

    /** 补齐一次发送需要的稳定字段，业务已显式设置的字段优先。 */
    public <T> MessageEnvelope<T> enrich(MessageEnvelope<T> message) {
        Objects.requireNonNull(message, "message must not be null");
        Instant now = options.clock().instant();
        MessageMetadata metadata = message.metadata();
        String messageId = firstText(metadata.messageId(), messageIdGenerator.nextId());
        Instant createdAt = metadata.createdAt() == null ? now : metadata.createdAt();
        Instant occurredAt = metadata.occurredAt() == null ? createdAt : metadata.occurredAt();
        String schemaVersion = firstText(metadata.schemaVersion(), options.defaultSchemaVersion());
        CurrentMessage current = contextAccessor.current().orElse(null);
        MessageContext explicit = message.context();
        MessageContext parent = current == null ? MessageContext.empty() : current.envelope().context();
        String source = firstText(explicit.source(), options.applicationName());
        String correlationId = firstText(explicit.correlationId(), firstText(parent.correlationId(), messageId));
        String causationId = firstText(explicit.causationId(), current == null ? null : current.envelope().messageId());
        String tenantId = firstText(explicit.tenantId(), parent.tenantId());
        return message.toBuilder()
                .messageId(messageId)
                .schemaVersion(schemaVersion)
                .occurredAt(occurredAt)
                .createdAt(createdAt)
                .context(new MessageContext(source, correlationId, causationId, tenantId))
                .build();
    }

    private static String firstText(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        return fallback == null || fallback.isBlank() ? null : fallback.trim();
    }
}
