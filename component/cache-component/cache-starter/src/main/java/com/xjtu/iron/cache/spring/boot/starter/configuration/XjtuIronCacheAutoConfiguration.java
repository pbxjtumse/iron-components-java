package com.xjtu.iron.cache.spring.boot.starter.configuration;

import com.xjtu.iron.cache.spring.boot.starter.properties.XjtuIronCacheProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * XJTU Iron Cache 自动装配总入口。
 *
 * <p>这个类只负责启用配置属性，不创建具体业务 Bean。</p>
 *
 * <p>具体 Bean 分散在：</p>
 *
 * <pre>
 * XjtuIronCacheObservabilityAutoConfiguration
 * XjtuIronCacheCaffeineAutoConfiguration
 * XjtuIronCacheRedisAutoConfiguration
 * XjtuIronCacheCoreAutoConfiguration
 * </pre>
 *
 * <p>这样避免一个 AutoConfiguration 类越来越大。</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(XjtuIronCacheProperties.class)
@ConditionalOnProperty(
        prefix = "xjtu.iron.cache",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class XjtuIronCacheAutoConfiguration {
}
