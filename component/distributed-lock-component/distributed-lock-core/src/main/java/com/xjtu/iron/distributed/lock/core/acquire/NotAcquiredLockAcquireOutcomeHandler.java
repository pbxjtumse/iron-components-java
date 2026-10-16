package com.xjtu.iron.distributed.lock.core.acquire;

import com.xjtu.iron.distributed.lock.api.model.LockHandle;
import com.xjtu.iron.distributed.lock.api.model.LockResult;
import com.xjtu.iron.distributed.lock.api.status.LockStage;
import com.xjtu.iron.distributed.lock.api.status.LockStatus;
import com.xjtu.iron.distributed.lock.core.observability.LockEventFactory;
import com.xjtu.iron.distributed.lock.core.observability.LockEventPublisher;
import com.xjtu.iron.distributed.lock.core.observability.LockEventType;
import com.xjtu.iron.distributed.lock.core.observability.LockMetricsFacade;
import com.xjtu.iron.distributed.lock.spi.protocol.acquire.LockAcquireStatus;

import java.util.Objects;

/** NOT_ACQUIRED 状态处理器。 */
public final class NotAcquiredLockAcquireOutcomeHandler implements LockAcquireOutcomeHandler {

    private final LockEventPublisher eventPublisher;
    private final LockEventFactory eventFactory;
    private final LockMetricsFacade metricsFacade;

    public NotAcquiredLockAcquireOutcomeHandler(LockEventPublisher eventPublisher, LockEventFactory eventFactory, LockMetricsFacade metricsFacade) {
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.eventFactory = Objects.requireNonNull(eventFactory, "eventFactory must not be null");
        this.metricsFacade = Objects.requireNonNull(metricsFacade, "metricsFacade must not be null");
    }

    @Override
    public LockAcquireStatus status() {
        return LockAcquireStatus.NOT_ACQUIRED;
    }

    @Override
    public LockResult<LockHandle> handle(LockAcquireOutcomeContext context) {
        metricsFacade.recordAcquire(context.provider().providerName(), context.options().getNamespace(), context.lockName(), false,
                context.waitDuration());
        LockStage stage = context.options().getWaitTime().isZero()
                ? LockStage.ACQUIRE : LockStage.WAIT;
        eventPublisher.publish(eventFactory.fromAcquireRequest(
                context.provider(), context.request(),
                LockEventType.NOT_ACQUIRED, stage, LockStatus.NOT_ACQUIRED, null));
        return LockResult.notAcquired(context.lockName(), null, stage, context.waitDuration());
    }
}
