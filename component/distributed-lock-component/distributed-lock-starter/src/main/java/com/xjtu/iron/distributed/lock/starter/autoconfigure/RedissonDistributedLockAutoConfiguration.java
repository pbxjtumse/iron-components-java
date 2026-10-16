package com.xjtu.iron.distributed.lock.starter.autoconfigure;

import com.xjtu.iron.distributed.lock.provider.redisson.RedissonLockKeyBuilder;
import com.xjtu.iron.distributed.lock.provider.redisson.RedissonLockProvider;
import com.xjtu.iron.distributed.lock.provider.redisson.RedissonOwnershipRegistry;
import com.xjtu.iron.distributed.lock.starter.properties.RedissonDistributedLockProperties;
import com.xjtu.iron.distributed.lock.starter.redisson.RedissonClientSelector;
import com.xjtu.iron.distributed.lock.starter.redisson.SpringRedisRedissonClientFactory;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisConnectionDetails;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Redisson LockProvider 自动配置。 */
@AutoConfiguration(after = RedisAutoConfiguration.class)
@EnableConfigurationProperties(RedissonDistributedLockProperties.class)
@ConditionalOnClass(RedissonClient.class)
@ConditionalOnProperty(prefix = "xjtu.iron.distributed-lock.redisson", name = "enabled", havingValue = "true")
public class RedissonDistributedLockAutoConfiguration {

    /**
     * 应用没有自行提供 RedissonClient 时，复用 Spring Boot Redis 连接参数自动创建一个。
     * 注意它只是复用连接参数，不会与 Lettuce 共用物理连接池。
     */
    @Bean(name = "distributedLockRedissonClient", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedissonClient.class)
    @ConditionalOnBean(RedisConnectionDetails.class)
    @ConditionalOnProperty(prefix = "xjtu.iron.distributed-lock.redisson", name = "create-client-if-missing", havingValue = "true",
            matchIfMissing = true)
    public RedissonClient distributedLockRedissonClient(RedisConnectionDetails connectionDetails, RedisProperties redisProperties,
            RedissonDistributedLockProperties properties) {
        return SpringRedisRedissonClientFactory.create(connectionDetails, redisProperties, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public RedissonLockKeyBuilder redissonLockKeyBuilder(RedissonDistributedLockProperties properties) {
        return new RedissonLockKeyBuilder(properties.getKeyPrefix());
    }

    @Bean
    @ConditionalOnMissingBean
    public RedissonOwnershipRegistry redissonOwnershipRegistry() {
        return new RedissonOwnershipRegistry();
    }

    @Bean
    @ConditionalOnBean(RedissonClient.class)
    @ConditionalOnMissingBean
    public RedissonLockProvider redissonLockProvider(ListableBeanFactory beanFactory, RedissonDistributedLockProperties properties,
            RedissonLockKeyBuilder keyBuilder, RedissonOwnershipRegistry ownershipRegistry) {
        RedissonClient client = RedissonClientSelector.select(beanFactory, properties.getClientBeanName());
        return new RedissonLockProvider(client, keyBuilder, ownershipRegistry, properties.getWatchdogTimeout());
    }
}
