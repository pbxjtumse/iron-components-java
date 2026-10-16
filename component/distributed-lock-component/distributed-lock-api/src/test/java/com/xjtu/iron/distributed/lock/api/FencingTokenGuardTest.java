package com.xjtu.iron.distributed.lock.api;

import com.xjtu.iron.distributed.lock.api.exception.FencingTokenRejectedException;
import com.xjtu.iron.distributed.lock.api.fencing.FencingTokenGuard;
import com.xjtu.iron.distributed.lock.api.model.LockHandle;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FencingTokenGuardTest {

    @Test
    void shouldAcceptConditionalWrite() {
        assertThatCode(() -> FencingTokenGuard.requireAccepted(handle(10L), token -> token == 10L))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectStaleToken() {
        assertThatThrownBy(() -> FencingTokenGuard.requireAccepted(handle(9L), token -> false))
                .isInstanceOf(FencingTokenRejectedException.class);
    }

    private LockHandle handle(long token) {
        return new LockHandle() {
            @Override public String lockName() { return "demo"; }
            @Override public String lockKey() { return "demo"; }
            @Override public String ownerToken() { return "owner"; }
            @Override public OptionalLong fencingToken() { return OptionalLong.of(token); }
            @Override public Instant acquiredAt() { return Instant.now(); }
            @Override public Duration leaseTime() { return Duration.ofSeconds(30); }
            @Override public Instant expireAt() { return Instant.now().plusSeconds(30); }
            @Override public boolean isLost() { return false; }
            @Override public boolean isReleaseAttempted() { return false; }
            @Override public boolean checkHeld() { return true; }
            @Override public boolean renew() { return true; }
            @Override public boolean unlock() { return true; }
            @Override public void assertHeld() { }
        };
    }
}
