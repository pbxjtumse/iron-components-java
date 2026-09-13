package com.xjtu.iron.distributed.lock.core.fencing;

import com.xjtu.iron.distributed.lock.api.model.LockOptions;
import com.xjtu.iron.distributed.lock.core.fencing.coordinator.FencingTokenCoordinator;
import com.xjtu.iron.distributed.lock.core.fencing.coordinator.FencingTokenMode;
import com.xjtu.iron.distributed.lock.core.fencing.coordinator.FencingTokenPlan;
import com.xjtu.iron.distributed.lock.core.fencing.registry.DefaultFencingTokenProviderRegistry;
import com.xjtu.iron.distributed.lock.spi.LockProvider;
import com.xjtu.iron.distributed.lock.spi.LockProviderCapabilities;
import com.xjtu.iron.distributed.lock.spi.protocol.acquire.LockAcquireRequest;
import com.xjtu.iron.distributed.lock.spi.protocol.acquire.LockAcquireResponse;
import com.xjtu.iron.distributed.lock.spi.protocol.check.LockCheckRequest;
import com.xjtu.iron.distributed.lock.spi.protocol.check.LockCheckResponse;
import com.xjtu.iron.distributed.lock.spi.protocol.release.LockReleaseRequest;
import com.xjtu.iron.distributed.lock.spi.protocol.release.LockReleaseResponse;
import com.xjtu.iron.distributed.lock.spi.protocol.renew.LockRenewRequest;
import com.xjtu.iron.distributed.lock.spi.protocol.renew.LockRenewResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FencingTokenCoordinatorTest {

    @Test
    void shouldChooseNativeProviderWhenSupported() {
        FencingTokenCoordinator coordinator = new FencingTokenCoordinator(new DefaultFencingTokenProviderRegistry(List.of()));
        FencingTokenPlan plan = coordinator.plan(lockProvider(true), LockOptions.builder().fencingRequired(true).build());
        assertThat(plan.mode()).isEqualTo(FencingTokenMode.NATIVE);
    }

    @Test
    void explicitLockProviderNameShouldChooseNativeFencing() {
        FencingTokenCoordinator coordinator = new FencingTokenCoordinator(new DefaultFencingTokenProviderRegistry(List.of()));
        FencingTokenPlan plan = coordinator.plan(lockProvider(true),
                LockOptions.builder().fencingRequired(true) .fencingTokenProviderName("lock").build());
        assertThat(plan.mode()).isEqualTo(FencingTokenMode.NATIVE);
    }

    @Test
    void explicitExternalProviderShouldOverrideNativeSupport() {
        com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenProvider external = provider("jdbc-sequence", 10L);
        FencingTokenCoordinator coordinator = new FencingTokenCoordinator(new DefaultFencingTokenProviderRegistry(List.of(external)));
        FencingTokenPlan plan = coordinator.plan(lockProvider(true),
                LockOptions.builder().fencingRequired(true) .fencingTokenProviderName("jdbc-sequence").build());
        assertThat(plan.mode()).isEqualTo(FencingTokenMode.EXTERNAL);
        assertThat(plan.externalProvider()).contains(external);
    }

    @Test
    void shouldFailFastWhenNoFencingSourceAvailable() {
        FencingTokenCoordinator coordinator = new FencingTokenCoordinator(new DefaultFencingTokenProviderRegistry(List.of()));
        assertThatThrownBy(() -> coordinator.plan(lockProvider(false),
                LockOptions.builder().fencingRequired(true).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("configure fencingTokenProviderName explicitly");
    }

    @Test
    void shouldNotGuessDefaultExternalProviderWhenProviderNameIsMissing() {
        com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenProvider external = provider("jdbc-sequence", 10L);
        FencingTokenCoordinator coordinator = new FencingTokenCoordinator(new DefaultFencingTokenProviderRegistry(List.of(external)));

        assertThatThrownBy(() -> coordinator.plan(lockProvider(false),
                LockOptions.builder().fencingRequired(true).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("configure fencingTokenProviderName explicitly");
    }

    private com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenProvider provider(String name, long token) {
        return new com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenProvider() {
            @Override public String providerName() { return name; }
            @Override public boolean supports(com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenRequest request) { return true; }
            @Override public com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenResponse nextToken(com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenRequest request) {
                return com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenResponse.issued(token);
            }
        };
    }

    private LockProvider lockProvider(boolean nativeFencing) {
        return new LockProvider() {
            @Override public String providerName() { return "lock"; }
            @Override public LockAcquireResponse acquire(LockAcquireRequest request) { throw new UnsupportedOperationException(); }
            @Override public LockReleaseResponse release(LockReleaseRequest request) { throw new UnsupportedOperationException(); }
            @Override public LockRenewResponse renew(LockRenewRequest request) { throw new UnsupportedOperationException(); }
            @Override public LockCheckResponse check(LockCheckRequest request) { throw new UnsupportedOperationException(); }
            @Override public LockProviderCapabilities capabilities() {
                return LockProviderCapabilities.builder().fencingTokenSupported(nativeFencing).build();
            }
        };
    }
}
