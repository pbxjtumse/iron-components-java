package com.xjtu.iron.cache.spring.boot.starter.configuration;

import com.xjtu.iron.cache.core.CacheProvider;
import com.xjtu.iron.cache.provider.redis.*;
import com.xjtu.iron.cache.spring.boot.starter.properties.XjtuIronCacheProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis L2 分布式缓存自动装配。
 *
 * <p>负责创建：</p>
 *
 * <pre>
 * RedisTemplate<String, byte[]>
 * RedisCacheSerializer
 * RedisBinaryClient
 * RedisCacheProvider
 * </pre>
 */
@AutoConfiguration(after = RedisAutoConfiguration.class)
@ConditionalOnProperty(
        prefix = "xjtu.iron.cache",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class XjtuIronCacheRedisAutoConfiguration {

    /**
     * 创建缓存组件专用 RedisTemplate。
     *
     * <p>该模板用于缓存值读写，value 使用 byte[]，避免 Spring 默认序列化器影响。</p>
     *
     * @param redisConnectionFactory Redis 连接工厂
     * @return 缓存组件专用 RedisTemplate
     */
    @Bean("ironCacheRedisTemplate")
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnMissingBean(name = "ironCacheRedisTemplate")
    public RedisTemplate<String, byte[]> ironCacheRedisTemplate(
            RedisConnectionFactory redisConnectionFactory
    ) {
        RedisTemplate<String, byte[]> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(RedisSerializer.byteArray());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(RedisSerializer.byteArray());
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

    /**
     * 创建 Redis 缓存值序列化器。
     *
     * @return Redis 缓存值序列化器
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisCacheSerializer redisCacheSerializer() {
        return new JacksonRedisCacheSerializer();
    }

    /**
     * 创建 Redis 二进制客户端。
     *
     * <p>RedisCacheProvider 通过 RedisBinaryClient 访问 Redis，避免直接依赖 RedisTemplate 细节。</p>
     *
     * @param redisTemplate 缓存组件专用 RedisTemplate
     * @return Redis 二进制客户端
     */
    @Bean
    @ConditionalOnBean(name = "ironCacheRedisTemplate")
    @ConditionalOnMissingBean
    public RedisBinaryClient redisBinaryClient(
            @Qualifier("ironCacheRedisTemplate") RedisTemplate<String, byte[]> redisTemplate
    ) {
        return new SpringDataRedisBinaryClient(redisTemplate);
    }

    /**
     * 创建 Redis Provider。
     *
     * <p>Redis Provider 只负责 L2 Redis 层的 get / put / evict。</p>
     *
     * @param redisBinaryClient Redis 二进制客户端
     * @param redisCacheSerializer Redis 缓存值序列化器
     * @param properties 缓存组件配置属性
     * @return Redis Provider
     */
    @Bean("ironRedisCacheProvider")
    @ConditionalOnBean(RedisBinaryClient.class)
    @ConditionalOnProperty(
            prefix = "xjtu.iron.cache.redis",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public CacheProvider redisCacheProvider(
            RedisBinaryClient redisBinaryClient,
            RedisCacheSerializer redisCacheSerializer,
            XjtuIronCacheProperties properties
    ) {
        return new RedisCacheProvider(
                redisBinaryClient,
                redisCacheSerializer,
                properties.getRedis().getKeyPrefix()
        );
    }
}
