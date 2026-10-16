package com.xjtu.iron.message.integration.pulsar.autoconfigure;

import com.xjtu.iron.message.integration.pulsar.PulsarMessageProvider;
import com.xjtu.iron.message.integration.pulsar.config.PulsarMessageProviderConfig;
import com.xjtu.iron.message.spi.MessageProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Pulsar Provider 自动配置。
 *
 * <p>只要 message-integration-pulsar 在 classpath 中，并且启用了
 * {@code xjtu.iron.message.pulsar.enabled=true} 或者未显式关闭，
 * 就创建 PulsarMessageProvider 作为 MessageProvider Bean。</p>
 */
@AutoConfiguration
@ConditionalOnClass(PulsarMessageProvider.class)
@EnableConfigurationProperties(PulsarMessageProperties.class)
@ConditionalOnProperty(prefix = "xjtu.iron.message.pulsar", name = "enabled", havingValue = "true", matchIfMissing = true)
/**
 * Pulsar Provider 的 Spring Boot 自动装配类。
 *
 * <p>它负责读取 Pulsar serviceUrl、listenerThreads、operationTimeout 等配置，并创建 Pulsar Provider Bean。
 * Provider 创建完成后由 message-starter 统一注册到 ProviderRegistry。</p>
 */
public class PulsarMessageAutoConfiguration {

    /**
     * 创建 Pulsar Provider 配置快照。
     *
     * @param properties Spring Boot 绑定后的 Pulsar 配置
     * @return Provider 配置快照
     */
    @Bean
    @ConditionalOnMissingBean
    public PulsarMessageProviderConfig pulsarMessageProviderConfig(
            PulsarMessageProperties properties) {
        return new PulsarMessageProviderConfig(
                properties.getServiceUrl(),
                properties.getOperationTimeout(),
                properties.getNegativeAckRedeliveryDelay(),
                properties.getReceiverQueueSize(),
                properties.getAuthenticationToken());
    }

    /**
     * 创建 Pulsar MessageProvider。
     *
     * @param config Provider 配置快照
     * @return Pulsar Provider
     */
    @Bean
    @ConditionalOnMissingBean(name = "pulsarMessageProvider")
    public MessageProvider pulsarMessageProvider(PulsarMessageProviderConfig config) {
        return new PulsarMessageProvider(config);
    }
}
