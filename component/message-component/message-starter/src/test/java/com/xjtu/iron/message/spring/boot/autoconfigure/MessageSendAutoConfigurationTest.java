package com.xjtu.iron.message.spring.boot.autoconfigure;

import com.xjtu.iron.message.core.MessageComponentOptions;
import com.xjtu.iron.message.core.routing.DestinationRoutingMode;
import com.xjtu.iron.message.core.send.DirectMessageSender;
import com.xjtu.iron.message.core.send.MessageSendExecutor;
import com.xjtu.iron.message.core.send.MessageSendReliabilityOptions;
import com.xjtu.iron.retry.api.execution.RetryExecutor;
import com.xjtu.iron.retry.api.policy.RetryPolicyRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.time.Clock;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证发送自动配置关于可靠发送执行器的保护逻辑。
 */
class MessageSendAutoConfigurationTest {

    @Test
    void shouldFailFastWhenSendReliabilityEnabledButRetryBeansAreMissing() {
        MessageSendAutoConfiguration configuration = new MessageSendAutoConfiguration();
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        ObjectProvider<RetryExecutor> retryExecutorProvider = beanFactory.getBeanProvider(RetryExecutor.class);
        ObjectProvider<RetryPolicyRegistry> retryPolicyRegistryProvider = beanFactory.getBeanProvider(RetryPolicyRegistry.class);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> configuration.messageSendExecutor(
                        options(),
                        MessageSendReliabilityOptions.defaults(),
                        retryExecutorProvider,
                        retryPolicyRegistryProvider));

        assertTrue(exception.getMessage().contains("RetryExecutor or RetryPolicyRegistry is missing"));
    }

    @Test
    void shouldUseDirectSenderWhenSendReliabilityIsDisabledEvenIfRetryBeansAreMissing() {
        MessageSendAutoConfiguration configuration = new MessageSendAutoConfiguration();
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        ObjectProvider<RetryExecutor> retryExecutorProvider = beanFactory.getBeanProvider(RetryExecutor.class);
        ObjectProvider<RetryPolicyRegistry> retryPolicyRegistryProvider = beanFactory.getBeanProvider(RetryPolicyRegistry.class);

        MessageSendExecutor executor = configuration.messageSendExecutor(
                options(),
                MessageSendReliabilityOptions.disabled(),
                retryExecutorProvider,
                retryPolicyRegistryProvider);

        assertTrue(executor instanceof DirectMessageSender);
    }

    private static MessageComponentOptions options() {
        return new MessageComponentOptions(
                "fake",
                "message-starter-test",
                "1",
                Duration.ofSeconds(3),
                DestinationRoutingMode.STRICT,
                Clock.systemUTC());
    }
}
