package com.xjtu.iron.distributed.lock.provider.redis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RedisLockKeyBuilderTest {
    @Test
    void shouldUseSameHashTagForLockAndFenceKey() {
        RedisLockKeyBuilder builder = new RedisLockKeyBuilder();
        assertEquals("iron:lock:{ns:job:1}:lock", builder.buildLockKey("ns", "job:1"));
        assertEquals("iron:lock:{ns:job:1}:fence", builder.buildFencingKey("ns", "job:1"));
    }
}
