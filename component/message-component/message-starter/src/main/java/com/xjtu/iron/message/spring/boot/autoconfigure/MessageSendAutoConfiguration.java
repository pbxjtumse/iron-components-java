package com.xjtu.iron.message.spring.boot.autoconfigure;

import com.xjtu.iron.message.core.MessageComponentOptions;
import com.xjtu.iron.message.core.send.DirectMessageSender;
import com.xjtu.iron.message.core.send.MessageSendExecutor;
import com.xjtu.iron.message.core.send.MessageSendReliabilityOptions;
import com.xjtu.iron.message.core.send.reliability.DefaultReliableMessageSender;
import com.xjtu.iron.message.spring.boot.autoconfigure.properties.MessageProperties;
import com.xjtu.iron.message.spring.boot.autoconfigure.properties.reliability.MessageSendReliabilityProperties;
import com.xjtu.iron.retry.api.execution.RetryExecutor;
import com.xjtu.iron.retry.api.policy.RetryPolicyRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.ForkJoinPool;

/**
 * 消息发送自动配置。
 */
@AutoConfiguration(after = MessageCoreAutoConfiguration.class)
@ConditionalOnProperty(prefix = "xjtu.iron.message", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MessageSendAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MessageSendReliabilityOptions messageSendReliabilityOptions(MessageProperties properties) {
        MessageSendReliabilityProperties send = properties.getReliability().getSend();
        return new MessageSendReliabilityOptions(
                send.isEnabled(),
                send.getRetryPolicy(),
                send.isRetryWhenUnknown(),
                send.isIncludeReliabilityInfo());
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageSendExecutor messageSendExecutor(
            MessageComponentOptions options,
            MessageSendReliabilityOptions reliabilityOptions,
            ObjectProvider<RetryExecutor> retryExecutorProvider,
            ObjectProvider<RetryPolicyRegistry> retryPolicyRegistryProvider) {
        if (!reliabilityOptions.enabled()) {
            return new DirectMessageSender(options.clock());
        }
        RetryExecutor retryExecutor = retryExecutorProvider.getIfAvailable();
        RetryPolicyRegistry retryPolicyRegistry = retryPolicyRegistryProvider.getIfAvailable();
        if (retryExecutor == null || retryPolicyRegistry == null) {
            throw new IllegalStateException(
                    "xjtu.iron.message.reliability.send.enabled=true, "
                            + "but RetryExecutor or RetryPolicyRegistry is missing. "
                            + "Please add retry-config/retry-core dependency or disable send reliability.");
        }
        return new DefaultReliableMessageSender(
                retryExecutor,
                retryPolicyRegistry,
                reliabilityOptions,
                options.clock(),
                ForkJoinPool.commonPool());
    }
}
