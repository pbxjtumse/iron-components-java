package com.xjtu.iron.distributed.lock.provider.redisson;

/** Redisson Provider 常量。 */
public final class RedissonLockConstants {

    /** LockOptions.providerName 中使用的稳定名称。 */
    public static final String PROVIDER_NAME = "redisson";

    /** 与自研 Redis Lua Provider 分开命名，便于同时启用、灰度和排查。 */
    public static final String DEFAULT_KEY_PREFIX = "iron:lock:redisson";

    private RedissonLockConstants() {}
}
