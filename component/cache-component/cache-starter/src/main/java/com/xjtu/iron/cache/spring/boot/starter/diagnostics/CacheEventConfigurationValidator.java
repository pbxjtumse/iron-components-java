package com.xjtu.iron.cache.spring.boot.starter.diagnostics;


import com.xjtu.iron.cache.core.event.CacheEventPublisher;
import com.xjtu.iron.cache.core.invalidate.LocalCacheInvalidator;
import com.xjtu.iron.cache.provider.redis.event.RedisCacheEventPublisher;
import com.xjtu.iron.cache.provider.redis.event.RedisCacheEventSubscriber;
import com.xjtu.iron.cache.spring.boot.starter.properties.XjtuIronCacheProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 缓存事件配置校验器。
 *
 * <p>用途：</p>
 *
 * <pre>
 * 当 xjtu.iron.cache.event.enabled=true 时，
 * 校验 Redis Pub/Sub 本地缓存失效链路是否完整装配。
 * </pre>
 *
 * <p>为什么要 fail-fast：</p>
 *
 * <pre>
 * 用户明确开启了事件功能，如果核心 Bean 没有创建，
 * 应用应该启动失败，而不是静默降级。
 * </pre>
 */
public class CacheEventConfigurationValidator implements SmartInitializingSingleton {

    /**
     * Spring 上下文。
     */
    private final ApplicationContext applicationContext;

    /**
     * 缓存配置。
     */
    private final XjtuIronCacheProperties properties;

    /**
     * 事件发布器。
     */
    private final ObjectProvider<CacheEventPublisher> cacheEventPublisherProvider;

    /**
     * Redis 事件订阅器。
     */
    private final ObjectProvider<RedisCacheEventSubscriber> subscriberProvider;

    /**
     * Redis 消息监听容器。
     */
    private final ObjectProvider<RedisMessageListenerContainer> listenerContainerProvider;

    /**
     * 本地缓存失效器。
     */
    private final ObjectProvider<LocalCacheInvalidator> localCacheInvalidatorProvider;

    public CacheEventConfigurationValidator(
            ApplicationContext applicationContext,
            XjtuIronCacheProperties properties,
            ObjectProvider<CacheEventPublisher> cacheEventPublisherProvider,
            ObjectProvider<RedisCacheEventSubscriber> subscriberProvider,
            ObjectProvider<RedisMessageListenerContainer> listenerContainerProvider,
            ObjectProvider<LocalCacheInvalidator> localCacheInvalidatorProvider
    ) {
        this.applicationContext = applicationContext;
        this.properties = properties;
        this.cacheEventPublisherProvider = cacheEventPublisherProvider;
        this.subscriberProvider = subscriberProvider;
        this.listenerContainerProvider = listenerContainerProvider;
        this.localCacheInvalidatorProvider = localCacheInvalidatorProvider;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!properties.getEvent().isEnabled()) {
            return;
        }

        CacheEventPublisher eventPublisher = cacheEventPublisherProvider.getIfAvailable();
        RedisCacheEventSubscriber subscriber = subscriberProvider.getIfAvailable();
        RedisMessageListenerContainer listenerContainer = listenerContainerProvider.getIfAvailable();
        LocalCacheInvalidator localCacheInvalidator = localCacheInvalidatorProvider.getIfAvailable();

        if (!(eventPublisher instanceof RedisCacheEventPublisher)) {
            throw new IllegalStateException(
                    "xjtu.iron.cache.event.enabled=true, but CacheEventPublisher is not RedisCacheEventPublisher. " +
                            "Actual=" + className(eventPublisher)
            );
        }

        if (subscriber == null) {
            throw new IllegalStateException(
                    "xjtu.iron.cache.event.enabled=true, but RedisCacheEventSubscriber is missing."
            );
        }

        if (listenerContainer == null) {
            throw new IllegalStateException(
                    "xjtu.iron.cache.event.enabled=true, but RedisMessageListenerContainer is missing."
            );
        }

        if (!applicationContext.containsBean("ironRedisCacheEventListenerContainer")) {
            throw new IllegalStateException(
                    "xjtu.iron.cache.event.enabled=true, but bean named ironRedisCacheEventListenerContainer is missing."
            );
        }

        if (localCacheInvalidator == null) {
            throw new IllegalStateException(
                    "xjtu.iron.cache.event.enabled=true, but LocalCacheInvalidator is missing."
            );
        }
    }

    private String className(Object bean) {
        return bean == null ? "NONE" : bean.getClass().getName();
    }
}
