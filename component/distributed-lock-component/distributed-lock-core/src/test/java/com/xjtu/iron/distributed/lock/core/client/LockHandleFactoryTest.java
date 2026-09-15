package com.xjtu.iron.distributed.lock.core.client;

import com.xjtu.iron.distributed.lock.api.model.LockOptions;
import com.xjtu.iron.distributed.lock.core.observability.LockEventFactory;
import com.xjtu.iron.distributed.lock.core.observability.LockMetricsFacade;
import com.xjtu.iron.distributed.lock.core.observability.NoOpLockEventPublisher;
import com.xjtu.iron.distributed.lock.core.observability.NoOpLockMetricsRecorder;
import com.xjtu.iron.distributed.lock.core.support.DefaultLockNamePatternResolver;
import com.xjtu.iron.distributed.lock.core.watchdog.LockWatchdog;
import com.xjtu.iron.distributed.lock.core.watchdog.WatchdogLockHandle;
import com.xjtu.iron.distributed.lock.spi.LockProvider;
import com.xjtu.iron.distributed.lock.spi.LockProviderCapabilities;
import com.xjtu.iron.distributed.lock.spi.protocol.acquire.LockAcquireRequest;
import com.xjtu.iron.distributed.lock.spi.protocol.acquire.LockAcquireResponse;
import com.xjtu.iron.distributed.lock.spi.protocol.check.LockCheckRequest;
import com.xjtu.iron.distributed.lock.spi.protocol.check.LockCheckResponse;
import com.xjtu.iron.distributed.lock.spi.protocol.common.LockLease;
import com.xjtu.iron.distributed.lock.spi.protocol.release.LockReleaseRequest;
import com.xjtu.iron.distributed.lock.spi.protocol.release.LockReleaseResponse;
import com.xjtu.iron.distributed.lock.spi.protocol.renew.LockRenewRequest;
import com.xjtu.iron.distributed.lock.spi.protocol.renew.LockRenewResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 验证 autoRenew 生命周期从 execute 收口到“成功 acquire -> Handle”。 */
class LockHandleFactoryTest {

    @Test
    void manualHandleShouldStartAndStopWatchdogWhenAutoRenewEnabled() {
        CountingWatchdog watchdog = new CountingWatchdog();
        LockHandleFactory factory = new LockHandleFactory(new NoOpLockEventPublisher(), new LockEventFactory(),
                new LockMetricsFacade(new NoOpLockMetricsRecorder(), new DefaultLockNamePatternResolver()), watchdog);

        LockOptions options = LockOptions.builder()
                .leaseTime(Duration.ofSeconds(30))
                .autoRenew(true)
                .maxRenewTime(Duration.ofMinutes(1))
                .build();

        Instant now = Instant.now();
        LockLease lease = LockLease.builder()
                .providerName("test")
                .namespace("demo")
                .lockName("order:1")
                .lockKey("lock-key")
                .ownerToken("owner-1")
                .leaseTime(Duration.ofSeconds(30))
                .acquiredAt(now)
                .expireAt(now.plusSeconds(30))
                .build();

        DefaultLockHandle handle = factory.create(new ReleasableProvider(), lease, options);
        assertEquals(1, watchdog.starts.get());

        handle.unlock();
        assertEquals(1, watchdog.stops.get());
    }

    private static final class CountingWatchdog implements LockWatchdog {
        private final AtomicInteger starts = new AtomicInteger();
        private final AtomicInteger stops = new AtomicInteger();
        @Override public void start(WatchdogLockHandle handle, LockOptions options) { starts.incrementAndGet(); }
        @Override public void stop(WatchdogLockHandle handle) { stops.incrementAndGet(); }
    }

    private static final class ReleasableProvider implements LockProvider {
        @Override public String providerName() { return "test"; }
        @Override public LockAcquireResponse acquire(LockAcquireRequest request) { throw new UnsupportedOperationException(); }
        @Override public LockReleaseResponse release(LockReleaseRequest request) { return LockReleaseResponse.released(); }
        @Override public LockRenewResponse renew(LockRenewRequest request) { return LockRenewResponse.renewed(Instant.now().plusSeconds(30)); }
        @Override public LockCheckResponse check(LockCheckRequest request) { return LockCheckResponse.held(); }
        @Override public LockProviderCapabilities capabilities() {
            return LockProviderCapabilities.builder().autoRenewSupported(true).manualRenewSupported(true).build();
        }
    }
}
