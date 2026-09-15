package com.xjtu.iron.distributed.lock.core.observability;

import com.xjtu.iron.distributed.lock.api.status.LockStage;
import com.xjtu.iron.distributed.lock.api.status.LockStatus;
import com.xjtu.iron.distributed.lock.spi.LockProvider;
import com.xjtu.iron.distributed.lock.spi.protocol.acquire.LockAcquireRequest;
import com.xjtu.iron.distributed.lock.spi.protocol.common.LockLease;

/** 统一创建 LockEvent，避免事件字段在 client/handle 中重复拼装。 */
public final class LockEventFactory {

    public LockEvent fromLease(LockLease lease, LockEventType type, LockStage stage, LockStatus status, Throwable error) {
        return LockEvent.builder()
                .eventType(type)
                .stage(stage)
                .status(status)
                .namespace(lease.getNamespace())
                .lockName(lease.getLockName())
                .lockKey(lease.getLockKey())
                .providerName(lease.getProviderName())
                .ownerToken(lease.getOwnerToken())
                .fencingToken(lease.fencingToken().isPresent() ? lease.fencingToken().getAsLong() : null)
                .fencingTokenProviderName(lease.fencingTokenProviderName().orElse(null))
                .error(error)
                .build();
    }

    public LockEvent fromAcquireRequest(LockProvider provider, LockAcquireRequest request, LockEventType type, LockStage stage, LockStatus status,
            Throwable error) {
        return LockEvent.builder()
                .eventType(type)
                .stage(stage)
                .status(status)
                .namespace(request.getNamespace())
                .lockName(request.getLockName())
                .providerName(provider.providerName())
                .ownerToken(request.getOwnerToken())
                .error(error)
                .build();
    }
    /** 创建 fencing 阶段事件，并允许在尚未拿到 token 时记录计划使用的发号 Provider。 */
    public LockEvent fromFencing(LockLease lease, LockEventType type, LockStatus status, String fencingTokenProviderName, Throwable error) {
        return LockEvent.builder()
                .eventType(type)
                .stage(LockStage.FENCING)
                .status(status)
                .namespace(lease.getNamespace())
                .lockName(lease.getLockName())
                .lockKey(lease.getLockKey())
                .providerName(lease.getProviderName())
                .ownerToken(lease.getOwnerToken())
                .fencingToken(lease.fencingToken().isPresent() ? lease.fencingToken().getAsLong() : null)
                .fencingTokenProviderName(fencingTokenProviderName)
                .error(error)
                .build();
    }

}
