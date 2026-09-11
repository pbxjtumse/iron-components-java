package com.xjtu.iron.distributed.lock.core.fencing.flow;

import com.xjtu.iron.distributed.lock.api.exception.LockProviderException;
import com.xjtu.iron.distributed.lock.core.fencing.coordinator.FencingTokenMode;
import com.xjtu.iron.distributed.lock.spi.LockProvider;
import com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenResponse;
import com.xjtu.iron.distributed.lock.spi.protocol.common.LockLease;

import java.time.Duration;
import java.util.Objects;

/**
 * Native fencing 流程。
 *
 * <p>Native 的含义是：LockProvider 在 acquire 成功的同时已经把 fencingToken 放进 {@link LockLease}。Redis Lua 的 INCR、Redisson
 * RFencedLock、未来 Etcd revision 都可以复用这个流程。Flow 不关心 token 如何产生，只校验“Provider 声称 native，却必须返回 token”。</p>
 */
public final class NativeFencingTokenFlow implements FencingTokenFlow {

    private final FencingTokenFlowSupport support;

    public NativeFencingTokenFlow(FencingTokenFlowSupport support) {
        this.support = Objects.requireNonNull(support, "support must not be null");
    }

    @Override
    public FencingTokenMode mode() {
        return FencingTokenMode.NATIVE;
    }

    @Override
    public FencingCompletion complete(FencingContext context) {
        LockProvider lockProvider = context.lockProvider();
        LockLease lease = context.lease();
        if (lease.fencingToken().isEmpty()) {
            FencingTokenResponse response = FencingTokenResponse.failed(new LockProviderException("native fencing token is missing: " + lockProvider.providerName()));
            return FencingCompletion.failure(support.fencingFailure(lockProvider, lease, context.waitDuration(), lockProvider.providerName(), response, Duration.ZERO));
        }
        support.recordFencingSuccess(lockProvider, lease, lockProvider.providerName(), Duration.ZERO);
        return FencingCompletion.success(lease);
    }
}
