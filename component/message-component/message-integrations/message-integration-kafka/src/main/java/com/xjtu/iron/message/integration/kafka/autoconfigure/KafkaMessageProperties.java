package com.xjtu.iron.message.integration.kafka.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kafka Provider 的 Spring Boot 配置属性。
 *
 * <p>该类只绑定 Kafka 原生连接与消费参数，通用消息参数仍放在
 * {@code xjtu.iron.message} 对应的 MessageProperties 中。</p>
 */
@ConfigurationProperties(prefix = "xjtu.iron.message.kafka")
/**
 * Kafka Provider 的配置属性模型。
 *
 * <p>这里保存 bootstrapServers、clientId、pollTimeout 等 Kafka 接入参数，
 * starter 会把这些配置转换成 {@code KafkaMessageProviderConfig}。</p>
 */
public class KafkaMessageProperties {

    /**
     * 是否启用 Kafka Provider 自动配置。
     */
    private boolean enabled = true;

    /**
     * Kafka bootstrap servers。
     *
     * <p>集群外访问时应填写 Kafka advertised.listeners 中暴露出来的地址。</p>
     */
    private String bootstrapServers = "localhost:9092";

    /**
     * Kafka client.id 前缀。
     *
     * <p>Provider 会在该前缀后追加 producer 或 consumer 标识。</p>
     */
    private String clientId = "iron-message-kafka";

    /**
     * Consumer 每次 poll 的等待时间。
     */
    private Duration pollTimeout = Duration.ofSeconds(1);

    /**
     * Handler 返回 RETRY 后的本地退避时间。
     */
    private Duration consumerRetryBackoff = Duration.ofSeconds(1);

    /**
     * 额外 Producer 原生配置。
     *
     * <p>bootstrap.servers、client.id、serializer、acks、enable.idempotence
     * 由组件统一管理，不允许在这里覆盖。</p>
     */
    private Map<String, Object> producerProperties = new LinkedHashMap<>();

    /**
     * 额外 Consumer 原生配置。
     *
     * <p>bootstrap.servers、client.id、group.id、deserializer、enable.auto.commit
     * 由组件统一管理，不允许在这里覆盖。</p>
     */
    private Map<String, Object> consumerProperties = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public Duration getPollTimeout() {
        return pollTimeout;
    }

    public void setPollTimeout(Duration pollTimeout) {
        this.pollTimeout = pollTimeout;
    }

    public Duration getConsumerRetryBackoff() {
        return consumerRetryBackoff;
    }

    public void setConsumerRetryBackoff(Duration consumerRetryBackoff) {
        this.consumerRetryBackoff = consumerRetryBackoff;
    }

    public Map<String, Object> getProducerProperties() {
        return producerProperties;
    }

    public void setProducerProperties(Map<String, Object> producerProperties) {
        this.producerProperties = producerProperties == null ? new LinkedHashMap<>() : producerProperties;
    }

    public Map<String, Object> getConsumerProperties() {
        return consumerProperties;
    }

    public void setConsumerProperties(Map<String, Object> consumerProperties) {
        this.consumerProperties = consumerProperties == null ? new LinkedHashMap<>() : consumerProperties;
    }
}
