package com.xjtu.iron.distributed.lock.core.client;

import com.xjtu.iron.distributed.lock.api.model.LockHandle;
import com.xjtu.iron.distributed.lock.api.model.LockOptions;
import com.xjtu.iron.distributed.lock.api.model.LockResult;
import com.xjtu.iron.distributed.lock.api.status.LockStage;
import com.xjtu.iron.distributed.lock.api.status.LockStatus;
import com.xjtu.iron.distributed.lock.spi.LockProvider;
import com.xjtu.iron.distributed.lock.spi.LockProviderCapabilities;
import com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenProvider;
import com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenRequest;
import com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenResponse;
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

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultDistributedLockClientFencingTest {

    @Test
    void externalProviderShouldEnrichLeaseAndDisableNativeFencing() {
        FakeLockProvider lockProvider = new FakeLockProvider(false);
        DefaultDistributedLockClient client = client(lockProvider, issuedProvider(42L));

        LockResult<LockHandle> result = client.tryLock("order:1",
                LockOptions.builder().fencingRequired(true) .fencingTokenProviderName("jdbc-sequence").build());

        assertThat(result.status()).isEqualTo(LockStatus.ACQUIRED);
        assertThat(result.fencingToken()).contains(42L);
        assertThat(result.fencingTokenProviderName()).contains("jdbc-sequence");
        assertThat(lockProvider.lastRequest.isNativeFencingRequired()).isFalse();
    }

    @Test
    void externalTokenMustNotBeReturnedWhenLeaseWasLostDuringIssuing() {
        FakeLockProvider lockProvider = new FakeLockProvider(false);
        lockProvider.checkResponse = LockCheckResponse.notOwner();
        DefaultDistributedLockClient client = client(lockProvider, issuedProvider(42L));

        LockResult<LockHandle> result = client.tryLock("order:lost",
                LockOptions.builder().fencingRequired(true) .fencingTokenProviderName("jdbc-sequence").build());

        assertThat(result.status()).isEqualTo(LockStatus.LOCK_LOST);
        assertThat(result.stage()).isEqualTo(LockStage.CHECK);
        assertThat(result.acquired()).isTrue();
        assertThat(result.handle()).isEmpty();
        assertThat(result.fencingToken()).contains(42L);
    }

    @Test
    void externalProviderFailureShouldReleaseLockAndReturnFencingProviderError() {
        FakeLockProvider lockProvider = new FakeLockProvider(false);
        DefaultDistributedLockClient client = client(lockProvider, failedProvider());

        LockResult<String> result = client.execute("order:1",
                LockOptions.builder().fencingRequired(true)
                        .fencingTokenProviderName("jdbc-sequence").build(),
                handle -> "must-not-run");

        assertThat(result.status()).isEqualTo(LockStatus.PROVIDER_ERROR);
        assertThat(result.stage()).isEqualTo(LockStage.FENCING);
        assertThat(result.acquired()).isTrue();
        assertThat(result.handle()).isEmpty();
        assertThat(lockProvider.releaseCount).isEqualTo(1);
    }

    private DefaultDistributedLockClient client(FakeLockProvider lockProvider, FencingTokenProvider tokenProvider) {
        return (DefaultDistributedLockClient) TestDistributedLockClients.create(lockProvider, List.of(tokenProvider));
    }

    private FencingTokenProvider issuedProvider(long token) {
        return new FencingTokenProvider() {
            @Override public String providerName() { return "jdbc-sequence"; }
            @Override public boolean supports(FencingTokenRequest request) { return true; }
            @Override public FencingTokenResponse nextToken(FencingTokenRequest request) {
                return FencingTokenResponse.issued(token);
            }
        };
    }

    private FencingTokenProvider failedProvider() {
        return new FencingTokenProvider() {
            @Override public String providerName() { return "jdbc-sequence"; }
            @Override public boolean supports(FencingTokenRequest request) { return true; }
            @Override public FencingTokenResponse nextToken(FencingTokenRequest request) {
                return FencingTokenResponse.failed(new IllegalStateException("db unavailable"));
            }
        };
    }

    private static final class FakeLockProvider implements LockProvider {
        private final boolean nativeFencing;
        private int releaseCount;
        private LockAcquireRequest lastRequest;
        private LockCheckResponse checkResponse = LockCheckResponse.held();

        private FakeLockProvider(boolean nativeFencing) {
            this.nativeFencing = nativeFencing;
        }

        @Override public String providerName() { return "redis"; }

        @Override
        public LockAcquireResponse acquire(LockAcquireRequest request) {
            this.lastRequest = request;
            LockLease.Builder builder = LockLease.builder()
                    .providerName("redis")
                    .namespace(request.getNamespace())
                    .lockName(request.getLockName())
                    .lockKey("key")
                    .ownerToken(request.getOwnerToken())
                    .leaseTime(request.getOptions().getLeaseTime())
                    .acquiredAt(Instant.now());
            if (request.isNativeFencingRequired()) {
                builder.fencingToken(1L).fencingTokenProviderName("redis");
            }
            return LockAcquireResponse.acquired(builder.build());
        }

        @Override public LockReleaseResponse release(LockReleaseRequest request) {
            releaseCount++;
            return LockReleaseResponse.released();
        }
        @Override public LockRenewResponse renew(LockRenewRequest request) {
            return LockRenewResponse.renewed(Instant.now());
        }
        @Override public LockCheckResponse check(LockCheckRequest request) {
            return checkResponse;
        }
        @Override public LockProviderCapabilities capabilities() {
            return LockProviderCapabilities.builder()
                    .autoRenewSupported(true)
                    .fencingTokenSupported(nativeFencing)
                    .build();
        }
    }
}
