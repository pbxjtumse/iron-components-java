package com.xjtu.iron.message.spring.boot.autoconfigure;

import com.xjtu.iron.message.core.MessageComponentOptions;
import com.xjtu.iron.message.core.provider.MessageProviderRegistry;
import com.xjtu.iron.message.core.routing.DefaultDestinationResolver;
import com.xjtu.iron.message.core.routing.DestinationResolver;
import com.xjtu.iron.message.core.routing.DestinationRoute;
import com.xjtu.iron.message.core.routing.DestinationRouteRegistry;
import com.xjtu.iron.message.spi.MessageProvider;
import com.xjtu.iron.message.spring.boot.autoconfigure.properties.MessageProperties;
import com.xjtu.iron.message.spring.boot.autoconfigure.properties.route.MessageRouteProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.Map;

/**
 * 消息 Provider 与路由自动配置。
 */
@AutoConfiguration(
        after = MessageCoreAutoConfiguration.class,
        afterName = {
                "com.xjtu.iron.message.integration.kafka.autoconfigure.KafkaMessageAutoConfiguration",
                "com.xjtu.iron.message.integration.pulsar.autoconfigure.PulsarMessageAutoConfiguration",
                "com.xjtu.iron.message.integration.rocketmq.autoconfigure.RocketMqMessageAutoConfiguration"
        })
@ConditionalOnProperty(prefix = "xjtu.iron.message", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MessageProviderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DestinationRouteRegistry destinationRouteRegistry(MessageProperties properties) {
        ArrayList<DestinationRoute> routes = new ArrayList<>();
        for (MessageRouteProperties route : properties.getRoutes()) {
            routes.add(new DestinationRoute(
                    route.getNamespace(),
                    route.getName(),
                    route.getProvider(),
                    route.getPhysicalName(),
                    Map.of()));
        }
        return new DestinationRouteRegistry(routes);
    }

    @Bean
    @ConditionalOnMissingBean
    public DestinationResolver destinationResolver(
            DestinationRouteRegistry routeRegistry,
            MessageComponentOptions options) {
        return new DefaultDestinationResolver(
                routeRegistry,
                options.defaultProviderName(),
                options.routingMode());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(MessageProvider.class)
    public MessageProviderRegistry messageProviderRegistry(ObjectProvider<MessageProvider> providers) {
        return new MessageProviderRegistry(providers.stream().toList());
    }
}
