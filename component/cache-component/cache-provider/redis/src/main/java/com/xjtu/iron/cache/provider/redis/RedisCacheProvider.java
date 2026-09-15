package com.xjtu.iron.cache.provider.redis;

import com.xjtu.iron.cache.api.enums.CacheLevel;
import com.xjtu.iron.cache.api.key.CacheKey;
import com.xjtu.iron.cache.api.model.CacheSpec;
import com.xjtu.iron.cache.api.model.CacheValue;
import com.xjtu.iron.cache.core.CacheProvider;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;

/**
 * Redis 分布式缓存 Provider。
 *
 * <p>作为 L2 缓存使用。它只负责 Redis 层读写，不负责 L1/L2 编排。</p>
 */
public class RedisCacheProvider implements CacheProvider {

    /** Redis 中表示空值缓存的特殊字节。 */
    private static final byte[] NULL_VALUE_BYTES = "__XJTU_IRON_CACHE_NULL__".getBytes(StandardCharsets.UTF_8);

    /** Redis 二进制客户端。 */
    private final RedisBinaryClient redisClient;

    /** Redis value 序列化器。 */
    private final RedisCacheSerializer serializer;

    /** Redis key 全局前缀，例如 cache:。 */
    private final String keyPrefix;

    /** 创建 Redis Provider。 */
    public RedisCacheProvider(RedisBinaryClient redisClient, RedisCacheSerializer serializer, String keyPrefix) {
        this.redisClient = redisClient;
        this.serializer = serializer;
        this.keyPrefix = keyPrefix == null ? "" : keyPrefix;
    }

    /** 返回 Provider 名称。 */
    @Override
    public String name() {
        return "redis";
    }

    /** 从 Redis 读取缓存。 */
    @Override
    public <T> CacheValue<T> get(CacheKey key, Class<T> valueType, CacheSpec spec) {
        if (!spec.isEnableL2()) {
            return CacheValue.miss();
        }

        byte[] bytes = redisClient.get(toRedisKey(key));
        if (bytes == null) {
            return CacheValue.miss();
        }
        if (isNullValue(bytes)) {
            return CacheValue.nullValue(CacheLevel.L2);
        }

        T value = serializer.deserialize(bytes, valueType);
        return CacheValue.of(value, CacheLevel.L2);
    }

    /** 写入正常值到 Redis。 */
    @Override
    public void put(CacheKey key, Object value, Duration ttl, CacheSpec spec) {
        if (!spec.isEnableL2()) {
            return;
        }
        byte[] bytes = serializer.serialize(value);
        redisClient.set(toRedisKey(key), bytes, ttl);
    }

    /** 写入空值占位到 Redis。 */
    @Override
    public void putNullValue(CacheKey key, Duration ttl, CacheSpec spec) {
        if (!spec.isEnableL2()) {
            return;
        }
        redisClient.set(toRedisKey(key), NULL_VALUE_BYTES, ttl);
    }

    /** 删除 Redis key。 */
    @Override
    public void evict(CacheKey key) {
        redisClient.del(toRedisKey(key));
    }

    /** 加上 Redis 全局前缀。 */
    private String toRedisKey(CacheKey key) {
        return keyPrefix + key.fullKey();
    }

    /** 判断 Redis value 是否为空值占位。 */
    private boolean isNullValue(byte[] bytes) {
        return Arrays.equals(NULL_VALUE_BYTES, bytes);
    }
}
