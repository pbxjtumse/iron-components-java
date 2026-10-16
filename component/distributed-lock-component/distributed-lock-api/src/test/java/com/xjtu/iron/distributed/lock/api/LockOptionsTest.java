package com.xjtu.iron.distributed.lock.api;

import com.xjtu.iron.distributed.lock.api.model.LockOptions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class LockOptionsTest {

    @Test
    void defaultsShouldUseNoWaitAndNoAutoRenew() {
        LockOptions options = LockOptions.defaults();
        assertEquals("default", options.getNamespace());
        assertEquals(Duration.ZERO, options.getWaitTime());
        assertEquals(LockWaitStrategy.NO_WAIT, options.getWaitStrategy());
        assertFalse(options.isAutoRenew());
    }

    @Test
    void waitForShouldUseBackoff() {
        LockOptions options = LockOptions.waitFor(Duration.ofSeconds(3));
        assertEquals(Duration.ofSeconds(3), options.getWaitTime());
        assertEquals(LockWaitStrategy.BACKOFF, options.getWaitStrategy());
    }

    @Test
    void invalidRenewIntervalShouldFail() {
        assertThrows(RuntimeException.class, () -> LockOptions.builder()
                .leaseTime(Duration.ofSeconds(10))
                .renewInterval(Duration.ofSeconds(10))
                .build());
    }
}
