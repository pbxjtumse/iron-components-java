package com.xjtu.iron.distributed.lock.core.fencing.flow;

import com.xjtu.iron.distributed.lock.api.model.LockHandle;
import com.xjtu.iron.distributed.lock.api.model.LockResult;
import com.xjtu.iron.distributed.lock.core.fencing.coordinator.FencingTokenMode;
import com.xjtu.iron.distributed.lock.spi.LockProvider;
import com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenResponse;
import com.xjtu.iron.distributed.lock.spi.protocol.common.LockLease;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * External fencing 流程。
 *
 * <p>External 的含义是：锁已经由 LockProvider 获取成功，但 token 需要另一个 Provider 独立生成，例如 JDBC sequence。这个流程最容易出错的点是
 * “发号期间锁可能已经过期”，因此发号成功后必须再次 check ownerToken，确认仍然持锁才允许创建 LockHandle 并进入业务 callback。</p>
 */
public final class ExternalFencingTokenFlow implements FencingTokenFlow {

    private final FencingTokenFlowSupport support;

    public ExternalFencingTokenFlow(FencingTokenFlowSupport support) {
        this.support = Objects.requireNonNull(support, "support must not be null");
    }

    @Override
    public FencingTokenMode mode() {
        return FencingTokenMode.EXTERNAL;
    }

    @Override
    public FencingCompletion complete(FencingContext context) {
        LockProvider lockProvider = context.lockProvider();
        LockLease lease = context.lease();
        String source = context.plan().sourceName(lockProvider.providerName());

        Instant fencingStart = support.now();
        FencingTokenResponse tokenResponse = support.issueExternal(context);
        Duration fencingDuration = Duration.between(fencingStart, support.now());
        if (!tokenResponse.isIssued()) {
            return FencingCompletion.failure(support.fencingFailure(lockProvider, lease, context.waitDuration(), source, tokenResponse, fencingDuration));
        }

        long token = tokenResponse.token().orElseThrow();
        LockLease fencedLease = lease.withFencingToken(token, source);
        support.recordFencingSuccess(lockProvider, fencedLease, source, fencingDuration);

        LockResult<LockHandle> ownershipFailure = support.verifyOwnershipAfterExternalFencing(lockProvider, fencedLease, context.waitDuration());
        if (ownershipFailure != null) {
            return FencingCompletion.failure(ownershipFailure);
        }
        return FencingCompletion.success(fencedLease);
    }
}
