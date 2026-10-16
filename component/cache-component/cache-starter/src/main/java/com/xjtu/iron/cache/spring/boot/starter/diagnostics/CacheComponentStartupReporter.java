package com.xjtu.iron.cache.spring.boot.starter.diagnostics;

import com.xjtu.iron.cache.api.CacheClient;
import com.xjtu.iron.cache.core.CacheProvider;
import com.xjtu.iron.cache.core.event.CacheEventPublisher;
import com.xjtu.iron.cache.core.invalidate.LocalCacheInvalidator;
import com.xjtu.iron.cache.spring.boot.starter.properties.XjtuIronCacheProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 缓存组件启动诊断报告器。
 *
 * <p>用途：</p>
 *
 * <pre>
 * 1. 在应用启动完成后打印缓存组件关键装配结果；
 * 2. 帮助快速判断 CacheClient、CacheProvider、事件发布器、事件监听器是否生效；
 * 3. 避免“配置打开了，但 Bean 没创建，功能静默失效”的问题难以排查。
 * </pre>
 *
 * <p>这个类只负责打印诊断信息，不做强校验。</p>
 *
 * <p>强校验由 CacheEventConfigurationValidator 负责。</p>
 */
public class CacheComponentStartupReporter implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(CacheComponentStartupReporter.class);

    /**
     * Spring 上下文。
     *
     * <p>用于按 Bean 名称检查某些关键 Bean 是否存在。</p>
     */
    private final ApplicationContext applicationContext;

    /**
     * 缓存组件配置属性。
     */
    private final XjtuIronCacheProperties properties;

    /**
     * CacheClient 提供器。
     *
     * <p>使用 ObjectProvider 是为了避免某些场景下 Bean 不存在导致诊断类本身启动失败。</p>
     */
    private final ObjectProvider<CacheClient> cacheClientProvider;

    /**
     * CacheProvider 提供器。
     */
    private final ObjectProvider<CacheProvider> cacheProviderProvider;

    /**
     * 缓存事件发布器提供器。
     */
    private final ObjectProvider<CacheEventPublisher> cacheEventPublisherProvider;

    /**
     * 本地缓存失效器提供器。
     */
    private final ObjectProvider<LocalCacheInvalidator> localCacheInvalidatorProvider;

    /**
     * Redis 消息监听容器提供器。
     */
    private final ObjectProvider<RedisMessageListenerContainer> redisMessageListenerContainerProvider;

    public CacheComponentStartupReporter(
            ApplicationContext applicationContext,
            XjtuIronCacheProperties properties,
            ObjectProvider<CacheClient> cacheClientProvider,
            ObjectProvider<CacheProvider> cacheProviderProvider,
            ObjectProvider<CacheEventPublisher> cacheEventPublisherProvider,
            ObjectProvider<LocalCacheInvalidator> localCacheInvalidatorProvider,
            ObjectProvider<RedisMessageListenerContainer> redisMessageListenerContainerProvider
    ) {
        this.applicationContext = applicationContext;
        this.properties = properties;
        this.cacheClientProvider = cacheClientProvider;
        this.cacheProviderProvider = cacheProviderProvider;
        this.cacheEventPublisherProvider = cacheEventPublisherProvider;
        this.localCacheInvalidatorProvider = localCacheInvalidatorProvider;
        this.redisMessageListenerContainerProvider = redisMessageListenerContainerProvider;
    }

    @Override
    public void afterSingletonsInstantiated() {
        CacheClient cacheClient = cacheClientProvider.getIfAvailable();
        CacheProvider cacheProvider = cacheProviderProvider.getIfAvailable();
        CacheEventPublisher eventPublisher = cacheEventPublisherProvider.getIfAvailable();
        LocalCacheInvalidator localCacheInvalidator = localCacheInvalidatorProvider.getIfAvailable();
        RedisMessageListenerContainer listenerContainer = redisMessageListenerContainerProvider.getIfAvailable();

        boolean hasIronRedisEventContainer =
                applicationContext.containsBean("ironRedisCacheEventListenerContainer");

        log.info("[CACHE-STARTUP] enabled={}", properties.isEnabled());
        log.info("[CACHE-STARTUP] applicationName={}", properties.getApplication().getName());
        log.info("[CACHE-STARTUP] instanceId={}", properties.getApplication().getInstanceId());

        log.info("[CACHE-STARTUP] caffeine.enabled={}", properties.getCaffeine().isEnabled());
        log.info("[CACHE-STARTUP] redis.enabled={}", properties.getRedis().isEnabled());

        log.info("[CACHE-STARTUP] event.enabled={}", properties.getEvent().isEnabled());
        log.info("[CACHE-STARTUP] event.channel={}", properties.getEvent().getChannel());
        log.info("[CACHE-STARTUP] event.publishFailurePolicy={}",
                properties.getEvent().getPublishFailurePolicy());

        log.info("[CACHE-STARTUP] cacheClient={}", className(cacheClient));
        log.info("[CACHE-STARTUP] cacheProvider={}", className(cacheProvider));
        log.info("[CACHE-STARTUP] cacheEventPublisher={}", className(eventPublisher));
        log.info("[CACHE-STARTUP] localCacheInvalidator={}", className(localCacheInvalidator));
        log.info("[CACHE-STARTUP] redisMessageListenerContainer={}", className(listenerContainer));
        log.info("[CACHE-STARTUP] ironRedisCacheEventListenerContainer.exists={}",
                hasIronRedisEventContainer);
    }

    private String className(Object bean) {
        return bean == null ? "NONE" : bean.getClass().getSimpleName();
    }
}
