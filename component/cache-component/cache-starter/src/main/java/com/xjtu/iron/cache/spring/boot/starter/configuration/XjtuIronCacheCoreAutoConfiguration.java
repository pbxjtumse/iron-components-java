package com.xjtu.iron.cache.spring.boot.starter.configuration;

import com.xjtu.iron.cache.api.CacheClient;
import com.xjtu.iron.cache.core.*;
import com.xjtu.iron.cache.core.event.CacheEventPublisher;
import com.xjtu.iron.cache.core.impl.DefaultCacheClient;
import com.xjtu.iron.cache.core.impl.DefaultCacheTtlResolver;
import com.xjtu.iron.cache.core.impl.LocalMutexCacheLoadGuard;
import com.xjtu.iron.cache.core.trace.CacheTraceContext;
import com.xjtu.iron.cache.core.trace.NoopCacheTraceContext;
import com.xjtu.iron.cache.provider.composite.CompositeCacheProvider;
import com.xjtu.iron.cache.spring.boot.starter.properties.XjtuIronCacheProperties;
import com.xjtu.iron.cache.spring.boot.starter.resolver.PropertiesCacheSpecResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 缓存核心流程自动装配。
 *
 * <p>负责创建缓存核心流程对象：</p>
 *
 * <pre>
 * CacheSpecResolver
 * CacheTtlResolver
 * CacheLoadGuard
 * CompositeCacheProvider
 * CacheClient
 * </pre>
 *
 * <p>这个类放在最后装配，因为它依赖 Caffeine Provider、Redis Provider、
 * CacheMetricsRecorder。</p>
 */
@AutoConfiguration(after = {
        XjtuIronCacheObservabilityAutoConfiguration.class,
        XjtuIronCacheCaffeineAutoConfiguration.class,
        XjtuIronCacheRedisAutoConfiguration.class,
        XjtuIronCacheEventAutoConfiguration.class
})
@ConditionalOnProperty(
        prefix = "xjtu.iron.cache",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class XjtuIronCacheCoreAutoConfiguration {

    /**
     * 创建基于配置文件的缓存策略解析器。
     *
     * @param properties 缓存组件配置属性
     * @return 缓存策略解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public CacheSpecResolver cacheSpecResolver(XjtuIronCacheProperties properties) {
        return new PropertiesCacheSpecResolver(properties);
    }

    /**
     * 创建 TTL 解析器。
     *
     * @return TTL 解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public CacheTtlResolver cacheTtlResolver() {
        return new DefaultCacheTtlResolver();
    }

    /**
     * 创建本地互斥加载保护器。
     *
     * <p>用于防止同一个 JVM 内同一个 key 被并发回源加载。</p>
     *
     * @return 缓存加载保护器
     */
    @Bean
    @ConditionalOnMissingBean
    public CacheLoadGuard cacheLoadGuard() {
        return new LocalMutexCacheLoadGuard();
    }

    /**
     * 创建 L1 + L2 组合 Provider。
     *
     * <p>它是 CacheClient 最终使用的 CacheProvider。</p>
     *
     * @param caffeineProvider L1 Caffeine Provider
     * @param redisProvider L2 Redis Provider
     * @return 组合 Provider
     */
    @Bean("ironCompositeCacheProvider")
    @Primary
    @ConditionalOnBean(name = {"ironCaffeineCacheProvider", "ironRedisCacheProvider"})
    public CacheProvider compositeCacheProvider(
            @Qualifier("ironCaffeineCacheProvider") CacheProvider caffeineProvider,
            @Qualifier("ironRedisCacheProvider") CacheProvider redisProvider
    ) {
        return new CompositeCacheProvider(caffeineProvider, redisProvider);
    }

    /**
     * 创建业务最终注入的 CacheClient。
     *
     * @param cacheProvider 组合 Provider
     * @param cacheSpecResolver 缓存策略解析器
     * @param cacheTtlResolver TTL 解析器
     * @param cacheLoadGuard 加载保护器
     * @param cacheMetricsRecorder 指标记录器
     * @return 缓存客户端
     */
    @Bean
    @ConditionalOnBean(name = "ironCompositeCacheProvider")
    @ConditionalOnMissingBean
    public CacheClient cacheClient(
            @Qualifier("ironCompositeCacheProvider") CacheProvider cacheProvider,
            CacheSpecResolver cacheSpecResolver,
            CacheTtlResolver cacheTtlResolver,
            CacheLoadGuard cacheLoadGuard,
            CacheMetricsRecorder cacheMetricsRecorder,
            CacheEventPublisher cacheEventPublisher,
            CacheTraceContext cacheTraceContext,
            XjtuIronCacheProperties properties
    ) {
        return new DefaultCacheClient(
                cacheProvider,
                cacheSpecResolver,
                cacheTtlResolver,
                cacheLoadGuard,
                cacheMetricsRecorder,
                cacheEventPublisher,
                properties.getApplication().getName(),
                properties.getApplication().getInstanceId(),
                properties.getEvent().getPublishFailurePolicy(),
                cacheTraceContext
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public CacheTraceContext cacheTraceContext() {
        return new NoopCacheTraceContext();
    }
}
