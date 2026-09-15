package com.xjtu.iron.distributed.lock.core.client;

import com.xjtu.iron.distributed.lock.api.client.DistributedLockClient;
import com.xjtu.iron.distributed.lock.api.model.LockHandle;
import com.xjtu.iron.distributed.lock.api.model.LockOptions;
import com.xjtu.iron.distributed.lock.api.model.LockResult;
import com.xjtu.iron.distributed.lock.api.status.LockStage;
import com.xjtu.iron.distributed.lock.api.status.LockStatus;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultDistributedLockClientTest {

    @Test
    void tryLockNoWaitNotAcquiredShouldUseAcquireStage() {
        DistributedLockClient client = client(new FakeProvider(false, LockReleaseResponse.released()));
        LockResult<LockHandle> result = client.tryLock("job:1", LockOptions.noWait());
        assertEquals(LockStatus.NOT_ACQUIRED, result.status());
        assertEquals(LockStage.ACQUIRE, result.stage());
    }

    @Test
    void executeManualUnlockInsideCallbackShouldRemainSuccess() {
        FakeProvider provider = new FakeProvider(true, LockReleaseResponse.released());
        DistributedLockClient client = client(provider);
        LockResult<String> result = client.execute("job:1", LockOptions.noWait(), handle -> {
            assertTrue(handle.unlock());
            return "ok";
        });
        assertEquals(LockStatus.SUCCESS, result.status());
        assertEquals("ok", result.value().orElse(null));
        assertEquals(1, provider.releaseCount);
    }

    private static DistributedLockClient client(FakeProvider provider) {
        return TestDistributedLockClients.create(provider);
    }

    private static final class FakeProvider implements LockProvider {
        private final boolean acquire;
        private final LockReleaseResponse releaseResponse;
        private int releaseCount;
        private FakeProvider(boolean acquire, LockReleaseResponse releaseResponse) { this.acquire = acquire; this.releaseResponse = releaseResponse; }
        @Override public String providerName() { return "redis"; }
        @Override public LockAcquireResponse acquire(LockAcquireRequest request) {
            if (!acquire) { return LockAcquireResponse.notAcquired(Duration.ofSeconds(1)); }
            LockLease lease = LockLease.builder().providerName("redis").namespace(request.getNamespace())
                    .lockName(request.getLockName()).lockKey("key").ownerToken(request.getOwnerToken())
                    .leaseTime(request.getOptions().getLeaseTime()).acquiredAt(Instant.now()).build();
            return LockAcquireResponse.acquired(lease);
        }
        @Override public LockReleaseResponse release(LockReleaseRequest request) { releaseCount++; return releaseResponse; }
        @Override public LockRenewResponse renew(LockRenewRequest request) { return LockRenewResponse.renewed(Instant.now()); }
        @Override public LockCheckResponse check(LockCheckRequest request) { return LockCheckResponse.held(); }
        @Override public LockProviderCapabilities capabilities() { return LockProviderCapabilities.builder().autoRenewSupported(true).build(); }
    }
}
