package com.xjtu.iron.foundation.time;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 过期时间模型，常用于缓存 TTL、幂等记录有效期和锁租约时间。
 */
public final class Expiration {

    private final Instant expireAt;

    private Expiration(Instant expireAt) {
        this.expireAt = expireAt;
    }

    public static Expiration after(Clock clock, Duration ttl) {
        return new Expiration(clock.instant().plus(ttl));
    }

    public Instant getExpireAt() { return expireAt; }

    public boolean isExpired(Clock clock) {
        return !clock.instant().isBefore(expireAt);
    }
}
