package com.xjtu.iron.message.integration.kafka.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;

import java.time.Duration;
import java.util.*;

/**
 * Kafka 基础 Provider 配置。
 *
 * <p>强类型字段由组件生命周期直接依赖；producerProperties 和 consumerProperties 只开放
 * Kafka 原生调优项。调用方不能覆盖组件必须保证的不变量。</p>
 *
 * <p>{@code bootstrapServers}：Kafka 集群引导地址</p>
 * <p>{@code clientIdPrefix}：Producer 和 Consumer client.id 前缀</p>
 * <p>{@code pollTimeout}：Consumer 每次 poll 的调用等待时间</p>
 * <p>{@code consumerRetryBackoff}：一期 Handler 返回 RETRY 后的本地退避，不等于 Kafka retry.backoff.ms</p>
 * <p>{@code producerProperties}：额外 Producer 原生调优属性</p>
 * <p>{@code consumerProperties}：额外 Consumer 原生调优属性</p>
 */
public final class KafkaMessageProviderConfig {
    /** Kafka 集群引导地址。 */
    private final String bootstrapServers;

    /** Producer 和 Consumer client.id 前缀。 */
    private final String clientIdPrefix;

    /** Consumer 每次 poll 的调用等待时间。 */
    private final Duration pollTimeout;

    /** 一期 Handler 返回 RETRY 后的本地退避，不等于 Kafka retry.backoff.ms。 */
    private final Duration consumerRetryBackoff;

    /** 额外 Producer 原生调优属性。 */
    private final Map<String, Object> producerProperties;

    /** 额外 Consumer 原生调优属性。 */
    private final Map<String, Object> consumerProperties;


    private static final Set<String> PROTECTED_PRODUCER_PROPERTIES = Set.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
            ProducerConfig.CLIENT_ID_CONFIG,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
            ProducerConfig.ACKS_CONFIG,
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG);

    private static final Set<String> PROTECTED_CONSUMER_PROPERTIES = Set.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            ConsumerConfig.CLIENT_ID_CONFIG,
            ConsumerConfig.GROUP_ID_CONFIG,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG);

    /** 校验强类型字段并复制原生属性。 */
    public KafkaMessageProviderConfig(
        String bootstrapServers,
        String clientIdPrefix,
        Duration pollTimeout,
        Duration consumerRetryBackoff,
        Map<String, Object> producerProperties,
        Map<String, Object> consumerProperties) {
        bootstrapServers = requireText(bootstrapServers, "bootstrapServers");
        clientIdPrefix = requireText(clientIdPrefix, "clientIdPrefix");
        if (pollTimeout == null || pollTimeout.isZero() || pollTimeout.isNegative()) {
            throw new IllegalArgumentException("pollTimeout must be positive");
        }
        if (consumerRetryBackoff == null || consumerRetryBackoff.isNegative()) {
            throw new IllegalArgumentException("consumerRetryBackoff must not be negative");
        }
        producerProperties = immutableProperties(producerProperties);
        consumerProperties = immutableProperties(consumerProperties);
        rejectProtected(producerProperties, PROTECTED_PRODUCER_PROPERTIES, "producerProperties");
        rejectProtected(consumerProperties, PROTECTED_CONSUMER_PROPERTIES, "consumerProperties");
    
        // 保存完成校验和标准化后的 bootstrapServers。
        this.bootstrapServers = bootstrapServers;
        // 保存完成校验和标准化后的 clientIdPrefix。
        this.clientIdPrefix = clientIdPrefix;
        // 保存完成校验和标准化后的 pollTimeout。
        this.pollTimeout = pollTimeout;
        // 保存完成校验和标准化后的 consumerRetryBackoff。
        this.consumerRetryBackoff = consumerRetryBackoff;
        // 保存完成校验和标准化后的 producerProperties。
        this.producerProperties = producerProperties;
        // 保存完成校验和标准化后的 consumerProperties。
        this.consumerProperties = consumerProperties;
    }

    public static KafkaMessageProviderConfig defaults(
            String bootstrapServers,
            String clientIdPrefix) {
        return new KafkaMessageProviderConfig(
                bootstrapServers,
                clientIdPrefix,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                Map.of(),
                Map.of());
    }

    private static Map<String, Object> immutableProperties(Map<String, Object> properties) {
        return properties == null || properties.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    private static void rejectProtected(
            Map<String, Object> properties,
            Set<String> protectedNames,
            String fieldName) {
        for (String name : protectedNames) {
            if (properties.containsKey(name)) {
                throw new IllegalArgumentException(
                        fieldName + " must not override component-managed property: " + name);
            }
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
    /**
     * 返回Kafka 集群引导地址。
     *
     * @return Kafka 集群引导地址
     */
    public String bootstrapServers() {
        // 返回不可变字段。
        return bootstrapServers;
    }

    /**
     * 返回Producer 和 Consumer client.id 前缀。
     *
     * @return Producer 和 Consumer client.id 前缀
     */
    public String clientIdPrefix() {
        // 返回不可变字段。
        return clientIdPrefix;
    }

    /**
     * 返回Consumer 每次 poll 的调用等待时间。
     *
     * @return Consumer 每次 poll 的调用等待时间
     */
    public Duration pollTimeout() {
        // 返回不可变字段。
        return pollTimeout;
    }

    /**
     * 返回一期 Handler 返回 RETRY 后的本地退避，不等于 Kafka retry.backoff.ms。
     *
     * @return 一期 Handler 返回 RETRY 后的本地退避，不等于 Kafka retry.backoff.ms
     */
    public Duration consumerRetryBackoff() {
        // 返回不可变字段。
        return consumerRetryBackoff;
    }

    /**
     * 返回额外 Producer 原生调优属性。
     *
     * @return 额外 Producer 原生调优属性
     */
    public Map<String, Object> producerProperties() {
        // 返回不可变字段。
        return producerProperties;
    }

    /**
     * 返回额外 Consumer 原生调优属性。
     *
     * @return 额外 Consumer 原生调优属性
     */
    public Map<String, Object> consumerProperties() {
        // 返回不可变字段。
        return consumerProperties;
    }

    /**
     * 按全部字段比较两个值对象。
     *
     * @param object 待比较对象
     * @return 字段值全部一致时返回 true
     */
    @Override
    public boolean equals(Object object) {
        // 同一对象直接相等。
        if (this == object) {
            return true;
        }
        // 类型不同或对象为空时不相等。
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        // 转换为当前类型后逐字段比较。
        KafkaMessageProviderConfig other = (KafkaMessageProviderConfig) object;
        return Objects.equals(bootstrapServers, other.bootstrapServers)
                && Objects.equals(clientIdPrefix, other.clientIdPrefix)
                && Objects.equals(pollTimeout, other.pollTimeout)
                && Objects.equals(consumerRetryBackoff, other.consumerRetryBackoff)
                && Objects.equals(producerProperties, other.producerProperties)
                && Objects.equals(consumerProperties, other.consumerProperties);
    }

    /**
     * 根据全部字段计算哈希值。
     *
     * @return 哈希值
     */
    @Override
    public int hashCode() {
        // 使用与 equals 相同的字段计算哈希值。
        return Objects.hash(bootstrapServers, clientIdPrefix, pollTimeout, consumerRetryBackoff, producerProperties, consumerProperties);
    }

    /**
     * 返回便于诊断的字段摘要。
     *
     * @return 字符串摘要
     */
    @Override
    public String toString() {
        // 拼接全部字段，保持值对象可诊断。
        return "KafkaMessageProviderConfig{" +
                "bootstrapServers=" + bootstrapServers +
                ", clientIdPrefix=" + clientIdPrefix +
                ", pollTimeout=" + pollTimeout +
                ", consumerRetryBackoff=" + consumerRetryBackoff +
                ", producerProperties=" + producerProperties +
                ", consumerProperties=" + consumerProperties +
                '}';
    }

}
