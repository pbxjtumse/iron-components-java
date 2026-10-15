package com.xjtu.iron.cache.spring.boot.starter.configuration.check;

import com.xjtu.iron.cache.api.CacheClient;
import com.xjtu.iron.cache.core.CacheProvider;
import com.xjtu.iron.cache.core.event.CacheEventPublisher;
import com.xjtu.iron.cache.core.invalidate.LocalCacheInvalidator;
import com.xjtu.iron.cache.provider.redis.event.RedisCacheEventSubscriber;
import com.xjtu.iron.cache.spring.boot.starter.configuration.XjtuIronCacheCoreAutoConfiguration;
import com.xjtu.iron.cache.spring.boot.starter.configuration.XjtuIronCacheEventAutoConfiguration;
import com.xjtu.iron.cache.spring.boot.starter.diagnostics.CacheComponentStartupReporter;
import com.xjtu.iron.cache.spring.boot.starter.diagnostics.CacheEventConfigurationValidator;
import com.xjtu.iron.cache.spring.boot.starter.properties.XjtuIronCacheProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 缓存组件诊断自动装配。
 *
 * <p>负责启动自检、启动日志和配置校验。</p>
 */
@AutoConfiguration(after = {XjtuIronCacheEventAutoConfiguration.class, XjtuIronCacheCoreAutoConfiguration.class})
@ConditionalOnProperty(prefix = "xjtu.iron.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
public class XjtuIronCacheDiagnosticsAutoConfiguration {

    /**
     * 启动诊断报告器。
     */
    @Bean
    public CacheComponentStartupReporter cacheComponentStartupReporter(ApplicationContext applicationContext,
        XjtuIronCacheProperties properties, ObjectProvider<CacheClient> cacheClientProvider, ObjectProvider<CacheProvider> cacheProviderProvider,
        ObjectProvider<CacheEventPublisher> cacheEventPublisherProvider, ObjectProvider<LocalCacheInvalidator> localCacheInvalidatorProvider,
        ObjectProvider<RedisMessageListenerContainer> redisMessageListenerContainerProvider) {
        return new CacheComponentStartupReporter(applicationContext, properties, cacheClientProvider, cacheProviderProvider,
            cacheEventPublisherProvider, localCacheInvalidatorProvider, redisMessageListenerContainerProvider);
    }

    /**
     * 事件配置强校验器。
     */
    @Bean
    public CacheEventConfigurationValidator cacheEventConfigurationValidator(ApplicationContext applicationContext,
        XjtuIronCacheProperties properties, ObjectProvider<CacheEventPublisher> cacheEventPublisherProvider,
        ObjectProvider<RedisCacheEventSubscriber> subscriberProvider, ObjectProvider<RedisMessageListenerContainer> listenerContainerProvider,
        ObjectProvider<LocalCacheInvalidator> localCacheInvalidatorProvider) {
        return new CacheEventConfigurationValidator(applicationContext, properties, cacheEventPublisherProvider, subscriberProvider,
            listenerContainerProvider, localCacheInvalidatorProvider);
    }
}
