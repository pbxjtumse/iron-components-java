package com.xjtu.iron.message.integration.rocketmq.autoconfigure;

import com.xjtu.iron.message.integration.rocketmq.RocketMqMessageProvider;
import com.xjtu.iron.message.integration.rocketmq.config.RocketMqMessageProviderConfig;
import com.xjtu.iron.message.spi.MessageProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * RocketMQ Provider 自动配置。
 *
 * <p>只要 message-integration-rocketmq 在 classpath 中，并且显式配置
 * {@code xjtu.iron.message.rocketmq.enabled=true}，就创建 RocketMQ MessageProvider。</p>
 */
@AutoConfiguration
@ConditionalOnClass(RocketMqMessageProvider.class)
@EnableConfigurationProperties(RocketMqMessageProperties.class)
@ConditionalOnProperty(prefix = "xjtu.iron.message.rocketmq", name = "enabled", havingValue = "true")
/**
 * RocketMQ4 Provider 的 Spring Boot 自动装配类。
 *
 * <p>当前实现面向 RocketMQ 4.x Remoting 客户端，读取 nameServer、producerGroup、consumerGroup、vipChannel 等配置，
 * 创建 {@code RocketMqMessageProvider} 并交给统一 ProviderRegistry 管理。</p>
 */
public class RocketMqMessageAutoConfiguration {

    /**
     * 创建 RocketMQ Provider 配置快照。
     *
     * @param properties Spring Boot 绑定后的 RocketMQ 配置
     * @return Provider 配置快照
     */
    @Bean
    @ConditionalOnMissingBean
    public RocketMqMessageProviderConfig rocketMqMessageProviderConfig(
            RocketMqMessageProperties properties) {
        Set<String> topics = new LinkedHashSet<>();
        if (properties.getTopics() != null) {
            for (String topic : properties.getTopics()) {
                if (topic != null && !topic.isBlank()) {
                    topics.add(topic.trim());
                }
            }
        }
        return new RocketMqMessageProviderConfig(
                properties.getNameServer(),
                properties.getProducerGroup(),
                topics,
                properties.getSendTimeout(),
                properties.getRetryTimesWhenSendFailed(),
                properties.getRetryTimesWhenSendAsyncFailed(),
                properties.isVipChannelEnabled(),
                properties.getConsumeFromWhere(),
                properties.getTagExpression(),
                properties.getAccessKey(),
                properties.getSecretKey());
    }

    /**
     * 创建 RocketMQ MessageProvider。
     *
     * @param config Provider 配置快照
     * @return RocketMQ Provider
     */
    @Bean
    @ConditionalOnMissingBean(name = "rocketMqMessageProvider")
    public MessageProvider rocketMqMessageProvider(RocketMqMessageProviderConfig config) {
        return new RocketMqMessageProvider(config);
    }
}
