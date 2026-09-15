package com.xjtu.iron.distributed.lock.starter.autoconfigure;

import com.xjtu.iron.distributed.lock.provider.redis.RedisLockKeyBuilder;
import com.xjtu.iron.distributed.lock.provider.redis.RedisLockProvider;
import com.xjtu.iron.distributed.lock.provider.redis.RedisLockScriptExecutor;
import com.xjtu.iron.distributed.lock.starter.properties.RedisDistributedLockProperties;
import com.xjtu.iron.distributed.lock.starter.redis.StringRedisTemplateRedisLockScriptExecutor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Redis 分布式锁自动配置。 */
@AutoConfiguration(after = RedisAutoConfiguration.class)
@EnableConfigurationProperties(RedisDistributedLockProperties.class)
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "xjtu.iron.distributed-lock.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisDistributedLockAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RedisLockKeyBuilder redisLockKeyBuilder(RedisDistributedLockProperties properties) {
        return new RedisLockKeyBuilder(properties.getKeyPrefix(), properties.getReleaseChannelPrefix(), properties.getFencingKeySuffix());
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean
    public RedisLockScriptExecutor redisLockScriptExecutor(StringRedisTemplate stringRedisTemplate) {
        return new StringRedisTemplateRedisLockScriptExecutor(stringRedisTemplate);
    }

    @Bean
    @ConditionalOnBean(RedisLockScriptExecutor.class)
    @ConditionalOnMissingBean
    public RedisLockProvider redisLockProvider(RedisLockScriptExecutor scriptExecutor, RedisLockKeyBuilder keyBuilder) {
        return new RedisLockProvider(scriptExecutor, keyBuilder);
    }
}
