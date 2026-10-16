package com.xjtu.iron.cache.spring.boot.starter.configuration.check;


import com.xjtu.iron.cache.spring.boot.starter.configuration.XjtuIronCacheCoreAutoConfiguration;
import com.xjtu.iron.cache.spring.boot.starter.configuration.XjtuIronCacheEventAutoConfiguration;
import com.xjtu.iron.cache.spring.boot.starter.diagnostics.CacheComponentStartupReporter;
import com.xjtu.iron.cache.spring.boot.starter.diagnostics.CacheEventConfigurationValidator;
import com.xjtu.iron.cache.spring.boot.starter.properties.XjtuIronCacheProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * 缓存组件诊断自动装配。
 *
 * <p>负责启动自检、启动日志和配置校验。</p>
 */
@AutoConfiguration(after = {
        XjtuIronCacheEventAutoConfiguration.class,
        XjtuIronCacheCoreAutoConfiguration.class
})
@ConditionalOnProperty(
        prefix = "xjtu.iron.cache",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class XjtuIronCacheDiagnosticsAutoConfiguration {

    /**
     * 启动诊断报告器。
     */
    @Bean
    public CacheComponentStartupReporter cacheComponentStartupReporter(
            ApplicationContext applicationContext,
            XjtuIronCacheProperties properties,
            org.springframework.beans.factory.ObjectProvider<com.xjtu.iron.cache.api.CacheClient> cacheClientProvider,
            org.springframework.beans.factory.ObjectProvider<com.xjtu.iron.cache.core.CacheProvider> cacheProviderProvider,
            org.springframework.beans.factory.ObjectProvider<com.xjtu.iron.cache.core.event.CacheEventPublisher> cacheEventPublisherProvider,
            org.springframework.beans.factory.ObjectProvider<com.xjtu.iron.cache.core.invalidate.LocalCacheInvalidator> localCacheInvalidatorProvider,
            org.springframework.beans.factory.ObjectProvider<org.springframework.data.redis.listener.RedisMessageListenerContainer> redisMessageListenerContainerProvider
    ) {
        return new CacheComponentStartupReporter(
                applicationContext,
                properties,
                cacheClientProvider,
                cacheProviderProvider,
                cacheEventPublisherProvider,
                localCacheInvalidatorProvider,
                redisMessageListenerContainerProvider
        );
    }

    /**
     * 事件配置强校验器。
     */
    @Bean
    public CacheEventConfigurationValidator cacheEventConfigurationValidator(
            ApplicationContext applicationContext,
            XjtuIronCacheProperties properties,
            org.springframework.beans.factory.ObjectProvider<com.xjtu.iron.cache.core.event.CacheEventPublisher> cacheEventPublisherProvider,
            org.springframework.beans.factory.ObjectProvider<com.xjtu.iron.cache.provider.redis.event.RedisCacheEventSubscriber> subscriberProvider,
            org.springframework.beans.factory.ObjectProvider<org.springframework.data.redis.listener.RedisMessageListenerContainer> listenerContainerProvider,
            org.springframework.beans.factory.ObjectProvider<com.xjtu.iron.cache.core.invalidate.LocalCacheInvalidator> localCacheInvalidatorProvider
    ) {
        return new CacheEventConfigurationValidator(
                applicationContext,
                properties,
                cacheEventPublisherProvider,
                subscriberProvider,
                listenerContainerProvider,
                localCacheInvalidatorProvider
        );
    }
}
