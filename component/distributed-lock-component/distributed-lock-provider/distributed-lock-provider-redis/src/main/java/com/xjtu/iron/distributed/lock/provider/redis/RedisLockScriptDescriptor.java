package com.xjtu.iron.distributed.lock.provider.redis;

import java.util.List;

/** Redis Lua 脚本描述，集中维护脚本路径和返回类型。 */
public enum RedisLockScriptDescriptor {
    ACQUIRE(RedisLockScripts.ACQUIRE, List.class),
    RELEASE(RedisLockScripts.RELEASE, Long.class),
    RENEW(RedisLockScripts.RENEW, Long.class),
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
