package com.xjtu.iron.message.core.codec;

import com.xjtu.iron.foundation.serialization.*;
import com.xjtu.iron.message.api.consume.context.ConsumeContext;
import com.xjtu.iron.message.api.consume.definition.ConsumerDefinition;
import com.xjtu.iron.message.api.model.*;
import com.xjtu.iron.message.spi.ProviderDestination;
import com.xjtu.iron.message.spi.ProviderInboundMessage;
import com.xjtu.iron.message.spi.ProviderSendRequest;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
/**
 * 统一消息线级协议编解码器，负责在 message-api 模型和 Provider SPI 请求之间转换。
 *
 * <p>注意它不是普通 JSON 序列化器。foundation-serialization 的 {@code Serializer} 只负责 payload
 * 对象与字节之间的转换，而 {@code MessageWireCodec} 负责把 messageId、messageType、messageKey、context、
 * headers、destination 等统一消息语义放入 Provider 请求或从 Provider 入站消息中还原出来。</p>
 *
 * <p>因此即使 payload 序列化统一复用 foundation-component，wire codec 这个边界也不能删除。
 * 它是 message-component 统一 Kafka、Pulsar、RocketMQ 线级协议的核心入口。</p>
 */
public final class MessageWireCodec {

    /** 一期无法从全部 Provider 获得统一投递次数时使用的基础值。 */
    private static final int INITIAL_DELIVERY_ATTEMPT = 1;

    /** 业务消息体序列化器，统一复用 foundation-serialization。 */
    private final Serializer payloadSerializer;

    /** 当前线级协议支持的 payload 媒体类型。 */
    private final String contentType;

    /** 单次 payload 序列化选项。 */
    private final SerializationOptions serializationOptions;

    /**
     * 创建线级消息编解码器。
     *
     * <p>{@code serializer}：消息体序列化器</p>
     */
    public MessageWireCodec(Serializer payloadSerializer) {
        this(
                payloadSerializer,
                SerializationFormat.JSON.getContentType(),
                SerializationOptions.builder().build());
    }

    public MessageWireCodec(
            Serializer payloadSerializer,
            String contentType,
            SerializationOptions serializationOptions) {
        this.payloadSerializer = Objects.requireNonNull(
                payloadSerializer,
                "payloadSerializer must not be null");
        this.contentType = requireText(contentType, "contentType must not be blank");
        this.serializationOptions = Objects.requireNonNull(
                serializationOptions,
                "serializationOptions must not be null");
    }

    /**
     * 将已丰富消息编码为 Provider 发送请求。
     *
     * <p>{@code destination}：逻辑目的地</p>
     * <p>{@code providerDestination}：已解析物理目的地</p>
     * <p>{@code message}：已丰富消息</p>
     * @return Provider 发送请求
     */
    public ProviderSendRequest encode(
            MessageDestination destination,
            ProviderDestination providerDestination,
            MessageEnvelope<?> message) {
        // 先通过 foundation-serialization 序列化业务消息体。
        SerializedPayload serializedPayload = payloadSerializer.serialize(
                message.payload(),
                serializationOptions,
                SerializationContext.builder()
                        .contentType(contentType)
                        .schemaVersion(message.metadata().schemaVersion())
                        .build());
        byte[] body = serializedPayload.getBody();
        // 构建完整线级消息头。
        Map<String, String> wireHeaders = toWireHeaders(destination, message);
        // 创建 Provider SPI 请求。
        return new ProviderSendRequest(
                providerDestination,
                message.messageId(),
                message.messageKey(),
                wireHeaders,
                body);
    }

    /**
     * 将 Provider 入站消息解码为统一信封和消费上下文。
     *
     * <p>{@code definition}：消费者定义</p>
     * <p>{@code providerDestination}：已解析物理目的地</p>
     * <p>{@code inbound}：Provider 原始入站消息</p>
     * @param <T> 业务消息体类型
     * @return 已解码入站对象
     */
    public <T> DecodedInbound<T> decode(
            ConsumerDefinition<T> definition,
            ProviderDestination providerDestination,
            ProviderInboundMessage inbound) {
        // 入站消息头不能为空 Map；ProviderInboundMessage 已完成标准化。
        Map<String, String> wireHeaders = inbound.headers();
        // 读取必填消息 ID。
        String messageId = requireHeader(
                wireHeaders,
                MessageHeaderNames.MESSAGE_ID);
        // 读取必填业务消息类型。
        String messageType = requireHeader(
                wireHeaders,
                MessageHeaderNames.MESSAGE_TYPE);
        // 读取必填结构版本。
        String schemaVersion = requireHeader(
                wireHeaders,
                MessageHeaderNames.SCHEMA_VERSION);
        // 校验线级逻辑目的地与当前订阅契约一致，防止错误路由被错误反序列化。
        validateLogicalDestination(definition, wireHeaders);

        // 校验媒体类型与当前序列化器一致。
        String contentType = requireHeader(
                wireHeaders,
                MessageHeaderNames.CONTENT_TYPE);
        // 不一致时拒绝盲目反序列化。
        if (!this.contentType.equalsIgnoreCase(contentType)) {
            // 一期只有单一 payload Serializer，因此不支持自动按 content-type 选择。
            throw new IllegalArgumentException(
                    "unsupported message content type: " + contentType
                            + ", expected=" + this.contentType);
        }

        // 解析业务事件发生时间。
        Instant occurredAt = parseInstant(
                requireHeader(wireHeaders, MessageHeaderNames.OCCURRED_AT),
                MessageHeaderNames.OCCURRED_AT);
        // 解析消息创建时间。
        Instant createdAt = parseInstant(
                requireHeader(wireHeaders, MessageHeaderNames.CREATED_AT),
                MessageHeaderNames.CREATED_AT);
        // 构造稳定消息元数据。
        MessageMetadata metadata = new MessageMetadata(
                messageId,
                messageType,
                schemaVersion,
                inbound.messageKey(),
                occurredAt,
                createdAt);
        // 构造稳定业务上下文，可选字段缺失时保持 null。
        MessageContext messageContext = new MessageContext(
                optionalHeader(wireHeaders, MessageHeaderNames.SOURCE),
                requireHeader(wireHeaders, MessageHeaderNames.CORRELATION_ID),
                optionalHeader(wireHeaders, MessageHeaderNames.CAUSATION_ID),
                optionalHeader(wireHeaders, MessageHeaderNames.TENANT_ID));
        // 删除组件系统头，只向业务暴露用户头和 trace 等非保留技术头。
        Map<String, String> userHeaders = extractUserHeaders(wireHeaders);
        // 反序列化业务消息体。
        T payload = payloadSerializer.deserialize(
                new SerializedPayload(
                        inbound.body(),
                        SerializationContext.builder()
                                .contentType(this.contentType)
                                .schemaVersion(schemaVersion)
                                .build()),
                definition.payloadType());
        // 构造统一入站消息信封。
        MessageEnvelope<T> envelope = MessageEnvelope.builder(messageType, payload)
                .messageId(metadata.messageId())
                .schemaVersion(metadata.schemaVersion())
                .messageKey(metadata.messageKey())
                .occurredAt(metadata.occurredAt())
                .createdAt(metadata.createdAt())
                .context(messageContext)
                .headers(MessageHeaders.of(userHeaders))
                .build();
        // 构造当前投递运行时上下文。
        ConsumeContext consumeContext = new ConsumeContext(
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
                java.time.Instant.now(),
                definition.reliabilityMode(),
                definition.idempotencyOptions().mode(),
                definition.idempotencyOptions().scene(),
                null,
                envelope.headers().asMap(),
                inbound.providerMetadata());
        // 返回信封和运行时上下文组合。
        return new DecodedInbound<>(envelope, consumeContext);
    }

    /**
     * 返回当前 payload 序列化器。
     *
     * @return foundation payload 序列化器
     */
    public Serializer payloadSerializer() {
        // 序列化器实例不可变引用，可安全返回。
        return payloadSerializer;
    }

    /**
     * 返回当前线级 payload 媒体类型。
     *
     * @return 媒体类型，例如 application/json
     */
    public String contentType() {
        return contentType;
    }

    /**
     * 构建完整线级消息头。
     */
    private Map<String, String> toWireHeaders(
            MessageDestination destination,
            MessageEnvelope<?> message) {
        // 提取结构化消息元数据。
        MessageMetadata metadata = message.metadata();
        // 使用有序 Map 保持日志和测试稳定。
        Map<String, String> headers = new LinkedHashMap<>(
                message.headers().asMap());
        // 写入消息唯一标识。
        headers.put(MessageHeaderNames.MESSAGE_ID, metadata.messageId());
        // 写入业务消息类型。
        headers.put(MessageHeaderNames.MESSAGE_TYPE, metadata.messageType());
        // 写入结构版本。
        headers.put(MessageHeaderNames.SCHEMA_VERSION, metadata.schemaVersion());
        // 写入业务事件发生时间。
        headers.put(
                MessageHeaderNames.OCCURRED_AT,
                metadata.occurredAt().toString());
        // 写入消息创建时间。
        headers.put(
                MessageHeaderNames.CREATED_AT,
                metadata.createdAt().toString());
        // 写入序列化媒体类型。
        headers.put(MessageHeaderNames.CONTENT_TYPE, contentType);
        // 写入逻辑目的地命名空间用于诊断和跨平台治理。
        headers.put(
                MessageHeaderNames.DESTINATION_NAMESPACE,
                destination.namespace());
        // 写入逻辑目的地名称。
        headers.put(
                MessageHeaderNames.DESTINATION_NAME,
                destination.name());
        // source 是可选字段，只在实际存在时写入。
        putIfText(
                headers,
                MessageHeaderNames.SOURCE,
                message.context().source());
        // correlationId 发送后通常存在，但仍按可选字段安全写入。
        putIfText(
                headers,
                MessageHeaderNames.CORRELATION_ID,
                message.context().correlationId());
        // 首条消息 causationId 通常为空。
        putIfText(
                headers,
                MessageHeaderNames.CAUSATION_ID,
                message.context().causationId());
        // 非多租户场景 tenantId 为空。
        putIfText(
                headers,
                MessageHeaderNames.TENANT_ID,
                message.context().tenantId());
        // 返回不可变线级消息头。
        return Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    }

    /**
     * 提取用户消息头。
     */
    private static Map<String, String> extractUserHeaders(
            Map<String, String> wireHeaders) {
        // 使用有序 Map 保留 Provider 返回顺序。
        Map<String, String> userHeaders = new LinkedHashMap<>();
        // 逐个筛选非系统头。
        wireHeaders.forEach((name, value) -> {
            // x-iron-message-* 系统头不向业务 headers 重复暴露。
            if (!MessageHeaderNames.isReserved(name)) {
                // traceparent、tracestate、baggage 等技术头会被保留。
                userHeaders.put(name, value);
            }
        });
        // 返回不可变用户头。
        return Collections.unmodifiableMap(new LinkedHashMap<>(userHeaders));
    }

    /**
     * 校验入站消息声明的逻辑目的地与消费者定义一致。
     */
    private static void validateLogicalDestination(
            ConsumerDefinition<?> definition,
            Map<String, String> headers) {
        // 读取线级命名空间。
        String namespace = requireHeader(
                headers,
                MessageHeaderNames.DESTINATION_NAMESPACE);
        // 读取线级逻辑名称。
        String name = requireHeader(
                headers,
                MessageHeaderNames.DESTINATION_NAME);
        // 当前消费者定义中的逻辑目的地。
        MessageDestination expected = definition.destination();
        // 任一字段不一致都拒绝按当前契约继续处理。
        if (!expected.namespace().equals(namespace)
                || !expected.name().equals(name)) {
            // 输出期望值和实际值，便于定位错误路由或 Topic 复用问题。
            throw new IllegalArgumentException(
                    "logical destination mismatch; expected="
                            + expected.qualifiedName()
                            + ", actual=" + namespace + ":" + name);
        }
    }

    /**
     * 写入非空白可选消息头。
     */
    private static void putIfText(
            Map<String, String> headers,
            String name,
            String value) {
        // 只有非空白值才进入线级消息头。
        if (value != null && !value.isBlank()) {
            // 去除首尾空白后写入。
            headers.put(name, value.trim());
        }
    }

    /**
     * 读取并规范化必填文本。
     */
    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    /**
     * 读取必填消息头。
     */
    private static String requireHeader(
            Map<String, String> headers,
            String name) {
        // 读取消息头值。
        String value = headers.get(name);
        // 缺失或空白都视为非法线级消息。
        if (value == null || value.isBlank()) {
            // 一期返回 RETRY；二期会进入毒消息和死信治理。
            throw new IllegalArgumentException(
                    "required message header missing: " + name);
        }
        // 返回标准化值。
        return value.trim();
    }

    /**
     * 读取可选消息头。
     */
    private static String optionalHeader(
            Map<String, String> headers,
            String name) {
        // 获取原始值。
        String value = headers.get(name);
        // 缺失或空白统一返回 null。
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 解析 ISO-8601 时间。
     */
    private static Instant parseInstant(
            String value,
            String headerName) {
        // 捕获格式异常并补充消息头名称。
        try {
            // Instant.parse 只接受明确时区的标准格式。
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            // 包装为参数异常，供消费流程统一转为 RETRY。
            throw new IllegalArgumentException(
                    "invalid instant message header: "
                            + headerName + "=" + value,
                    exception);
        }
    }

    /**
     * 表示已经完成解码的入站消息。
     *
     * @param <T> 业务消息体类型
     */
    public static final class DecodedInbound<T> {

        /** 统一消息信封。 */
        private final MessageEnvelope<T> envelope;

        /** 消费运行时上下文。 */
        private final ConsumeContext consumeContext;

        /**
         * 创建已经完成解码的入站消息。
         *
         * @param envelope 统一消息信封
         * @param consumeContext 消费运行时上下文
         */
        public DecodedInbound(
                MessageEnvelope<T> envelope,
                ConsumeContext consumeContext) {
            // 信封不能为空。
            this.envelope = Objects.requireNonNull(
                    envelope,
                    "envelope must not be null");
            // 消费上下文不能为空。
            this.consumeContext = Objects.requireNonNull(
                    consumeContext,
                    "consumeContext must not be null");
        }

        /**
         * 返回统一消息信封。
         *
         * @return 统一消息信封
         */
        public MessageEnvelope<T> envelope() {
            // 返回不可变字段。
            return envelope;
        }

        /**
         * 返回消费运行时上下文。
         *
         * @return 消费运行时上下文
         */
        public ConsumeContext consumeContext() {
            // 返回不可变字段。
            return consumeContext;
        }
    }
}
