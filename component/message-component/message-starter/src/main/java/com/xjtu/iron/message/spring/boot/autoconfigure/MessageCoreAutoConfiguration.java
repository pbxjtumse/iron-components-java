package com.xjtu.iron.message.spring.boot.autoconfigure;

import com.xjtu.iron.foundation.id.api.StringIdGenerator;
import com.xjtu.iron.foundation.id.nanoid.NanoIdStringIdGenerator;
import com.xjtu.iron.foundation.id.registry.StringIdGeneratorRegistry;
import com.xjtu.iron.foundation.serialization.Serializer;
import com.xjtu.iron.foundation.serialization.jackson.JacksonJsonSerializer;
import com.xjtu.iron.message.core.MessageComponentOptions;
import com.xjtu.iron.message.core.codec.MessageWireCodec;
import com.xjtu.iron.message.core.context.MessageContextAccessor;
import com.xjtu.iron.message.core.context.ThreadLocalMessageContextAccessor;
import com.xjtu.iron.message.core.enrich.MessageEnvelopeEnricher;
import com.xjtu.iron.message.spring.boot.autoconfigure.properties.MessageProperties;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.util.List;
import java.util.Locale;

/**
 * 消息组件核心对象自动配置。
 *
 * <p>只负责和具体 MQ 无关的基础能力：根配置绑定、运行参数、序列化器、线级编解码器、
 * 当前消息上下文访问器、消息 ID 生成器和发送前 Envelope 丰富器。</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(MessageProperties.class)
@ConditionalOnProperty(prefix = "xjtu.iron.message", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MessageCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Serializer messagePayloadSerializer(MessageProperties properties) {
        String serializerType = properties.getSerializer().getType();
        if (serializerType != null
                && !serializerType.isBlank()
                && !"jackson".equals(serializerType.trim().toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(
                    "Unsupported xjtu.iron.message.serializer.type: " + serializerType
                            + ". Current starter only provides jackson. "
                            + "Declare a custom Serializer bean if you need another serializer.");
        }
        return new JacksonJsonSerializer();
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageComponentOptions messageComponentOptions(MessageProperties properties) {
        return new MessageComponentOptions(
                properties.getProvider(),
                properties.getApplicationName(),
                properties.getDefaultSchemaVersion(),
                properties.getDefaultConfirmTimeout(),
                properties.getRoutingMode(),
                Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageWireCodec messageWireCodec(Serializer payloadSerializer) {
        return new MessageWireCodec(payloadSerializer);
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageContextAccessor messageContextAccessor() {
        return new ThreadLocalMessageContextAccessor();
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageEnvelopeEnricher messageEnvelopeEnricher(
            MessageComponentOptions options,
            @Qualifier("messageStringIdGenerator") StringIdGenerator messageIdGenerator,
            MessageContextAccessor contextAccessor) {
        return new MessageEnvelopeEnricher(options, messageIdGenerator, contextAccessor);
    }

    @Bean("messageStringIdGenerator")
    @ConditionalOnMissingBean(name = "messageStringIdGenerator")
    public StringIdGenerator messageStringIdGenerator(ListableBeanFactory beanFactory) {
        StringIdGenerator fromRegistry = resolveFromRegistry(beanFactory);
        if (fromRegistry != null) {
            return fromRegistry;
        }
        StringIdGenerator fromBean = resolveNamedStringIdGenerator(beanFactory);
        if (fromBean != null) {
            return fromBean;
        }
        return new NanoIdStringIdGenerator();
    }

    private static StringIdGenerator resolveFromRegistry(ListableBeanFactory beanFactory) {
        String[] registryNames = beanFactory.getBeanNamesForType(StringIdGeneratorRegistry.class, false, false);
        for (String registryName : registryNames) {
            StringIdGeneratorRegistry registry = beanFactory.getBean(registryName, StringIdGeneratorRegistry.class);
            if (registry.contains("message")) {
                return registry.require("message");
            }
            if (registry.contains("default")) {
                return registry.require("default");
            }
        }
        return null;
    }

    private static StringIdGenerator resolveNamedStringIdGenerator(ListableBeanFactory beanFactory) {
        for (String preferredName : List.of("messageIdGenerator", "defaultStringIdGenerator")) {
            if (beanFactory.containsBean(preferredName)) {
                return beanFactory.getBean(preferredName, StringIdGenerator.class);
            }
        }
        return null;
    }
}
