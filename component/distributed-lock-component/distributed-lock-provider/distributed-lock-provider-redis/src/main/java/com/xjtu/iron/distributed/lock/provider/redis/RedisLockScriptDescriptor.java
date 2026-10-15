package com.xjtu.iron.distributed.lock.provider.redis;

import java.util.List;

/** Redis Lua 脚本描述，集中维护脚本路径和返回类型。 */
public enum RedisLockScriptDescriptor {

    /** 原子尝试获取锁，并返回锁租约所需的复合结果。 */
    ACQUIRE(RedisLockScripts.ACQUIRE, List.class),

    /** 校验 owner token 后原子释放锁。 */
    RELEASE(RedisLockScripts.RELEASE, Long.class),

    /** 校验 owner token 后原子延长锁的有效期。 */
    RENEW(RedisLockScripts.RENEW, Long.class),

    /** 检查指定 owner token 是否仍持有锁。 */
    CHECK(RedisLockScripts.CHECK, Long.class);

    private final String location;
    private final Class<?> resultType;

    RedisLockScriptDescriptor(String location, Class<?> resultType) {
        this.location = location;
        this.resultType = resultType;
    }

    public String getLocation() {
        return location;
    }

    public Class<?> getResultType() {
        return resultType;
    }

    public static RedisLockScriptDescriptor fromLocation(String location) {
        for (RedisLockScriptDescriptor descriptor : values()) {
            if (descriptor.location.equals(location)) {
                return descriptor;
            }
        }
        throw new IllegalArgumentException("unknown redis lock script: " + location);
    }
}
