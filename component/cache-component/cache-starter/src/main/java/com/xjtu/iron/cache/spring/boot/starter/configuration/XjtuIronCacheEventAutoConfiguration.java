package com.xjtu.iron.cache.spring.boot.starter.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjtu.iron.cache.core.event.CacheEventHandler;
import com.xjtu.iron.cache.core.event.CacheEventPublisher;
import com.xjtu.iron.cache.core.event.DefaultCacheEventHandler;
import com.xjtu.iron.cache.core.event.NoopCacheEventPublisher;
import com.xjtu.iron.cache.core.invalidate.LocalCacheInvalidator;
import com.xjtu.iron.cache.provider.redis.event.JacksonRedisCacheEventSerializer;
import com.xjtu.iron.cache.provider.redis.event.RedisCacheEventPublisher;
import com.xjtu.iron.cache.provider.redis.event.RedisCacheEventSerializer;
import com.xjtu.iron.cache.provider.redis.event.RedisCacheEventSubscriber;
import com.xjtu.iron.cache.spring.boot.starter.properties.XjtuIronCacheProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 缓存事件自动装配。
 *
 * <p>二期主要用于 Redis Pub/Sub 本地缓存失效通知。</p>
 */
@AutoConfiguration(after = RedisAutoConfiguration.class)
public class XjtuIronCacheEventAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(XjtuIronCacheEventAutoConfiguration.class);

    /**
     * 默认事件发布器。
     *
     * <p>如果没有开启 Redis Pub/Sub 事件能力，则使用 Noop 实现。</p>
     */
    @Bean
    @ConditionalOnMissingBean(CacheEventPublisher.class)
    public CacheEventPublisher noopCacheEventPublisher() {
        log.info("[CACHE-EVENT-AUTO-CONFIG] create NoopCacheEventPublisher");
        return new NoopCacheEventPublisher();
    }

    /**
     * Redis 事件序列化器。
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "xjtu.iron.cache.event",
            name = "enabled",
            havingValue = "true"
    )
    @ConditionalOnMissingBean
    public RedisCacheEventSerializer redisCacheEventSerializer(ObjectMapper objectMapper) {
        log.info("[CACHE-EVENT-AUTO-CONFIG] create RedisCacheEventSerializer");
        return new JacksonRedisCacheEventSerializer(objectMapper);
    }

    /**
     * Redis Pub/Sub 事件发布器。
     *
     * <p>开启事件能力后，它会作为主 CacheEventPublisher。</p>
     */
    @Bean
    @Primary
    @ConditionalOnProperty(
            prefix = "xjtu.iron.cache.event",
            name = "enabled",
            havingValue = "true"
    )
    public CacheEventPublisher redisCacheEventPublisher(
            StringRedisTemplate stringRedisTemplate,
            RedisCacheEventSerializer serializer,
            XjtuIronCacheProperties properties
    ) {
        log.info("[CACHE-EVENT-AUTO-CONFIG] create RedisCacheEventPublisher, channel={}",
                properties.getEvent().getChannel());

        return new RedisCacheEventPublisher(
                stringRedisTemplate,
                serializer,
                properties.getEvent().getChannel()
        );
    }

    /**
     * 默认缓存事件处理器。
     *
     * <p>收到其他实例的 EVICT_KEY 事件后，删除当前实例本地 Caffeine。</p>
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "xjtu.iron.cache.event",
            name = "enabled",
            havingValue = "true"
    )
    @ConditionalOnMissingBean
    public CacheEventHandler cacheEventHandler(
            LocalCacheInvalidator localCacheInvalidator,
            XjtuIronCacheProperties properties
    ) {
        log.info("[CACHE-EVENT-AUTO-CONFIG] create CacheEventHandler, currentInstanceId={}",
                properties.getApplication().getInstanceId());

        return new DefaultCacheEventHandler(
                localCacheInvalidator,
                properties.getApplication().getInstanceId()
        );
    }

    /**
     * Redis Pub/Sub 订阅器。
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "xjtu.iron.cache.event",
            name = "enabled",
            havingValue = "true"
    )
    @ConditionalOnMissingBean
    public RedisCacheEventSubscriber redisCacheEventSubscriber(
            RedisCacheEventSerializer serializer,
            CacheEventHandler eventHandler
    ) {
        log.info("[CACHE-EVENT-AUTO-CONFIG] create RedisCacheEventSubscriber");
        return new RedisCacheEventSubscriber(serializer, eventHandler);
    }

    /**
     * Redis 消息监听容器。
     *
     * <p>这个 Bean 真正负责向 Redis 订阅 channel。</p>
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "xjtu.iron.cache.event",
            name = "enabled",
            havingValue = "true"
    )
    @ConditionalOnMissingBean(name = "ironRedisCacheEventListenerContainer")
    public RedisMessageListenerContainer ironRedisCacheEventListenerContainer(
            RedisConnectionFactory redisConnectionFactory,
            RedisCacheEventSubscriber subscriber,
            XjtuIronCacheProperties properties
    ) {
        String channel = properties.getEvent().getChannel();

        log.info("[CACHE-EVENT-AUTO-CONFIG] create RedisMessageListenerContainer, subscribe channel={}",
                channel);

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(channel));
        return container;
    }
}