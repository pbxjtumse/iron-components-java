package com.xjtu.iron.message.spring.boot.autoconfigure;

import com.xjtu.iron.message.core.MessageComponentOptions;
import com.xjtu.iron.message.core.MessageTemplate;
import com.xjtu.iron.message.core.codec.MessageWireCodec;
import com.xjtu.iron.message.core.consume.MessageConsumerAdapter;
import com.xjtu.iron.message.core.enrich.MessageEnvelopeEnricher;
import com.xjtu.iron.message.core.provider.MessageProviderRegistry;
import com.xjtu.iron.message.core.routing.DestinationResolver;
import com.xjtu.iron.message.core.send.MessageSendExecutor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * MessageTemplate 门面自动配置。
 */
@AutoConfiguration(after = {
        MessageCoreAutoConfiguration.class,
        MessageProviderAutoConfiguration.class,
        MessageSendAutoConfiguration.class,
        MessageConsumeAutoConfiguration.class
})
@ConditionalOnProperty(prefix = "xjtu.iron.message", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MessageTemplateAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(MessageProviderRegistry.class)
    public MessageTemplate messageTemplate(
            MessageComponentOptions options,
            MessageProviderRegistry providerRegistry,
            DestinationResolver destinationResolver,
            MessageEnvelopeEnricher envelopeEnricher,
            MessageWireCodec wireCodec,
            MessageSendExecutor sendExecutor,
            MessageConsumerAdapter consumerAdapter) {
        return new MessageTemplate(
                options,
                providerRegistry,
                destinationResolver,
                envelopeEnricher,
                wireCodec,
                sendExecutor,
                consumerAdapter);
    }
}
