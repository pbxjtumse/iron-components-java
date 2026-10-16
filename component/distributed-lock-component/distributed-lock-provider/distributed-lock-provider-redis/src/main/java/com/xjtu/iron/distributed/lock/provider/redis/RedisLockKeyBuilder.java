package com.xjtu.iron.distributed.lock.provider.redis;

/**
 * Redis 锁 key 构造器。
 *
 * <p>Redis 的 lockKey、fencingKey、releaseChannel 都属于 Redis Provider 的物理命名规则，
 * 不应该泄漏到通用 LockProvider SPI 中。</p>
 *
 * <p>注意 Redis Cluster 场景：acquire.lua 可能同时使用 lockKey 和 fencingKey。
 * 多 key Lua 脚本要求这些 key 位于同一个 hash slot。因此这里使用 Redis hash tag：
 * {@code iron:lock:{namespace:lockName}:lock} 与 {@code iron:lock:{namespace:lockName}:fence}
 * 会落到同一个 slot。</p>
 */
public final class RedisLockKeyBuilder {

    /** Redis key 前缀。 */
    private final String keyPrefix;

    /** Redis 释放通知 channel 前缀。Provider-native waiting 扩展时可使用。 */
    private final String releaseChannelPrefix;

    /** fencing key 后缀。Redis 原生 INCR fencing 使用。 */
    private final String fencingKeySuffix;

    public RedisLockKeyBuilder() {
        this(RedisLockConstants.DEFAULT_KEY_PREFIX, RedisLockConstants.DEFAULT_RELEASE_CHANNEL_PREFIX, RedisLockConstants.DEFAULT_FENCING_KEY_SUFFIX);
    }

    public RedisLockKeyBuilder(String keyPrefix, String releaseChannelPrefix, String fencingKeySuffix) {
        this.keyPrefix = trimColon(requireText(keyPrefix, "keyPrefix"));
        this.releaseChannelPrefix = trimColon(requireText(releaseChannelPrefix, "releaseChannelPrefix"));
        this.fencingKeySuffix = trimColon(requireText(fencingKeySuffix, "fencingKeySuffix"));
    }

    /**
     * 构造 Redis 锁 key。
     *
     * @param namespace 命名空间。
     * @param lockName  业务锁名称。
     * @return Redis 锁 key。
     */
    public String buildLockKey(String namespace, String lockName) {
        return keyPrefix + ":{" + buildHashTag(namespace, lockName) + "}:lock";
    }

    /**
     * 构造 Redis fencing token key。
     *
     * @param namespace 命名空间。
     * @param lockName  业务锁名称。
     * @return Redis fencing key。
     */
    public String buildFencingKey(String namespace, String lockName) {
        return keyPrefix + ":{" + buildHashTag(namespace, lockName) + "}:" + fencingKeySuffix;
    }

    /**
     * 构造 Redis 解锁发布 channel。
     *
     * <p>当前自研 Redis Provider 不实现 provider-native waiting，因此该 channel 暂时只作为预留。</p>
     */
    public String buildReleaseChannel(String namespace, String lockName) {
        return releaseChannelPrefix + ':' + buildHashTag(namespace, lockName);
    }

    private static String buildHashTag(String namespace, String lockName) {
        return normalize(namespace) + ':' + normalize(lockName);
    }

    private static String normalize(String value) {
        return trimColon(requireText(value, "redis key part"));
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String trimColon(String value) {
        String result = value;
        while (result.startsWith(":")) {
            result = result.substring(1);
        }
        while (result.endsWith(":")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
