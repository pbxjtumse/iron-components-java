package com.xjtu.iron.distributed.lock.core.wait;

import com.xjtu.iron.distributed.lock.spi.LockProvider;
import com.xjtu.iron.distributed.lock.spi.protocol.acquire.LockAcquireRequest;

import java.time.Clock;
import java.util.Objects;

/**
 * 锁等待上下文。
 */
public final class LockWaitContext {

    private final LockAcquireRequest request;
    private final LockProvider provider;
    private final Clock clock;

    public LockWaitContext(LockAcquireRequest request, LockProvider provider, Clock clock) {
        this.request = Objects.requireNonNull(request, "request must not be null");
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public LockAcquireRequest getRequest() { return request; }
    public LockProvider getProvider() { return provider; }
    public Clock getClock() { return clock; }
}
