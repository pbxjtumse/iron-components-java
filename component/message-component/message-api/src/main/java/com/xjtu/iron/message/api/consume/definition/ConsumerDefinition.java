package com.xjtu.iron.message.api.consume.definition;

import com.xjtu.iron.message.api.model.MessageDestination;

import java.util.Objects;

/**
 * 定义一个消息消费者。
 *
 * <p>v13 在原有 destination、consumerGroup、payloadType 的基础上，增加可靠性、幂等和事务配置。
 * 老构造器仍然保留，默认使用 AT_LEAST_ONCE、不开启幂等、不开启事务。</p>
 *
 * @param <T> 业务消息体类型
 */
public final class ConsumerDefinition<T> {
    /** 本地消费者定义 ID，用于配置合并、日志和启动校验，不一定等于 MQ consumerGroup。 */
    private final String consumerId;

    /** 显式指定的 Provider 名称；为空时由 destination/providerHint 或默认 Provider 决定。 */
    private final String providerName;

    /** 业务逻辑目的地，例如 namespace=order、name=paid。 */
    private final MessageDestination destination;

    /** MQ 消费组，用于 Kafka offset、Pulsar subscription、RocketMQ consumer group 等 Provider 语义。 */
    private final String consumerGroup;

    /** 业务 Handler 接收的 payload 类型，用于 MessageWireCodec 反序列化。 */
    private final Class<T> payloadType;

    /** 消费可靠性目标，例如 AT_LEAST_ONCE 或 EFFECTIVELY_ONCE。 */
    private final ConsumerReliabilityMode reliabilityMode;

    /** 当前消费者的消费幂等配置。 */
    private final MessageIdempotencyOptions idempotencyOptions;

    /** 当前消费者的事务模板配置。 */
    private final MessageConsumeTransactionOptions transactionOptions;

    public ConsumerDefinition(MessageDestination destination, String consumerGroup, Class<T> payloadType) {
        this(
                consumerGroup,
                null,
                destination,
                consumerGroup,
                payloadType,
                ConsumerReliabilityMode.AT_LEAST_ONCE,
                MessageIdempotencyOptions.disabled(),
                MessageConsumeTransactionOptions.disabled());
    }

    public ConsumerDefinition(
            String consumerId,
            String providerName,
            MessageDestination destination,
            String consumerGroup,
            Class<T> payloadType,
            ConsumerReliabilityMode reliabilityMode,
            MessageIdempotencyOptions idempotencyOptions,
            MessageConsumeTransactionOptions transactionOptions) {
        this.consumerId = textOrDefault(consumerId, consumerGroup);
        this.providerName = normalize(providerName);
        this.destination = Objects.requireNonNull(destination, "destination must not be null");
        this.consumerGroup = requireText(consumerGroup, "consumerGroup must not be blank");
        this.payloadType = Objects.requireNonNull(payloadType, "payloadType must not be null");
        this.reliabilityMode = reliabilityMode == null ? ConsumerReliabilityMode.AT_LEAST_ONCE : reliabilityMode;
        this.idempotencyOptions = idempotencyOptions == null ? MessageIdempotencyOptions.disabled() : idempotencyOptions;
        this.transactionOptions = transactionOptions == null ? MessageConsumeTransactionOptions.disabled() : transactionOptions;
    }

    public static <T> ConsumerDefinition<T> of(MessageDestination destination, String consumerGroup, Class<T> payloadType) {
        return new ConsumerDefinition<>(destination, consumerGroup, payloadType);
    }

    public String consumerId() { return consumerId; }
    public String providerName() { return providerName; }
    public MessageDestination destination() { return destination; }
    public String consumerGroup() { return consumerGroup; }
    public Class<T> payloadType() { return payloadType; }
    public ConsumerReliabilityMode reliabilityMode() { return reliabilityMode; }
    public MessageIdempotencyOptions idempotencyOptions() { return idempotencyOptions; }
    public MessageConsumeTransactionOptions transactionOptions() { return transactionOptions; }

    private static String textOrDefault(String value, String defaultValue) {
        String normalized = normalize(value);
        return normalized == null ? requireText(defaultValue, "default value must not be blank") : normalized;
    }

    private static String requireText(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        ConsumerDefinition<?> other = (ConsumerDefinition<?>) object;
        return Objects.equals(consumerId, other.consumerId)
                && Objects.equals(providerName, other.providerName)
                && Objects.equals(destination, other.destination)
                && Objects.equals(consumerGroup, other.consumerGroup)
                && Objects.equals(payloadType, other.payloadType)
                && reliabilityMode == other.reliabilityMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(consumerId, providerName, destination, consumerGroup, payloadType, reliabilityMode);
    }
}
