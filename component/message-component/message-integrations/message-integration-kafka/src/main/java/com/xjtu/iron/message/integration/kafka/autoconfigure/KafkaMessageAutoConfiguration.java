package com.xjtu.iron.message.integration.kafka.autoconfigure;

import com.xjtu.iron.message.integration.kafka.KafkaMessageProvider;
import com.xjtu.iron.message.integration.kafka.config.KafkaMessageProviderConfig;
import com.xjtu.iron.message.spi.MessageProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Kafka Provider 自动配置。
 *
 * <p>只要 message-integration-kafka 在 classpath 中，并且启用了
 * {@code xjtu.iron.message.kafka.enabled=true} 或者未显式关闭，
 * 就创建 KafkaMessageProvider 作为 MessageProvider Bean。</p>
 */
@AutoConfiguration
@ConditionalOnClass(KafkaMessageProvider.class)
@EnableConfigurationProperties(KafkaMessageProperties.class)
@ConditionalOnProperty(prefix = "xjtu.iron.message.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
/**
 * Kafka Provider 的 Spring Boot 自动装配类。
 *
 * <p>它读取 {@code xjtu.iron.message.kafka.*} 配置，创建 Kafka Provider 所需的配置对象和 Provider Bean，
 * 再交给 message-starter 的 Provider 注册表统一管理。</p>
 */
public class KafkaMessageAutoConfiguration {

    /**
     * 创建 Kafka Provider 配置快照。
     *
     * @param properties Spring Boot 绑定后的 Kafka 配置
     * @return Provider 配置快照
     */
    @Bean
    @ConditionalOnMissingBean
    public KafkaMessageProviderConfig kafkaMessageProviderConfig(KafkaMessageProperties properties) {
        return new KafkaMessageProviderConfig(
                properties.getBootstrapServers(),
                properties.getClientId(),
                properties.getPollTimeout(),
                properties.getConsumerRetryBackoff(),
                properties.getProducerProperties(),
                properties.getConsumerProperties());
    }

    /**
     * 创建 Kafka MessageProvider。
     *
     * @param config Provider 配置快照
     * @return Kafka Provider
     */
    @Bean
    @ConditionalOnMissingBean(name = "kafkaMessageProvider")
    public MessageProvider kafkaMessageProvider(KafkaMessageProviderConfig config) {
        return new KafkaMessageProvider(config);
    }
}
