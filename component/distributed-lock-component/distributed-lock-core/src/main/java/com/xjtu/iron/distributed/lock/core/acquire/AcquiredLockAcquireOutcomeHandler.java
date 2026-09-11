package com.xjtu.iron.distributed.lock.core.acquire;

import com.xjtu.iron.distributed.lock.api.model.LockHandle;
import com.xjtu.iron.distributed.lock.api.model.LockResult;
import com.xjtu.iron.distributed.lock.api.status.LockStage;
import com.xjtu.iron.distributed.lock.api.status.LockStatus;
import com.xjtu.iron.distributed.lock.core.client.DefaultLockHandle;
import com.xjtu.iron.distributed.lock.core.client.LockHandleFactory;
import com.xjtu.iron.distributed.lock.core.fencing.flow.FencingCompletion;
import com.xjtu.iron.distributed.lock.core.fencing.flow.FencingContext;
import com.xjtu.iron.distributed.lock.core.fencing.flow.FencingTokenFlow;
import com.xjtu.iron.distributed.lock.core.fencing.flow.FencingTokenFlowRegistry;
import com.xjtu.iron.distributed.lock.core.observability.LockEventFactory;
import com.xjtu.iron.distributed.lock.core.observability.LockEventPublisher;
import com.xjtu.iron.distributed.lock.core.observability.LockEventType;
import com.xjtu.iron.distributed.lock.core.observability.LockMetricsFacade;
import com.xjtu.iron.distributed.lock.spi.protocol.acquire.LockAcquireStatus;
import com.xjtu.iron.distributed.lock.spi.protocol.common.LockLease;

import java.util.Objects;

/** ACQUIRED 状态处理器：记录获取成功、完成 fencing 准备并创建 LockHandle。 */
public final class AcquiredLockAcquireOutcomeHandler implements LockAcquireOutcomeHandler {

    private final FencingTokenFlowRegistry fencingTokenFlowRegistry;
    private final LockHandleFactory lockHandleFactory;
    private final LockEventPublisher eventPublisher;
    private final LockEventFactory eventFactory;
    private final LockMetricsFacade metricsFacade;

    public AcquiredLockAcquireOutcomeHandler(FencingTokenFlowRegistry fencingTokenFlowRegistry, LockHandleFactory lockHandleFactory,
            LockEventPublisher eventPublisher, LockEventFactory eventFactory, LockMetricsFacade metricsFacade) {
        this.fencingTokenFlowRegistry = Objects.requireNonNull(fencingTokenFlowRegistry, "fencingTokenFlowRegistry must not be null");
        this.lockHandleFactory = Objects.requireNonNull(lockHandleFactory, "lockHandleFactory must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.eventFactory = Objects.requireNonNull(eventFactory, "eventFactory must not be null");
        this.metricsFacade = Objects.requireNonNull(metricsFacade, "metricsFacade must not be null");
    }

    @Override
    public LockAcquireStatus status() {
        return LockAcquireStatus.ACQUIRED;
    }

    @Override
    public LockResult<LockHandle> handle(LockAcquireOutcomeContext context) {
        LockLease lease = context.response().getLease();
        metricsFacade.recordAcquire(context.provider().providerName(), context.options().getNamespace(), lease.getLockName(), true,
                context.waitDuration());
        eventPublisher.publish(eventFactory.fromLease(lease, LockEventType.ACQUIRED, LockStage.ACQUIRE, LockStatus.ACQUIRED, null));

        FencingContext fencingContext = FencingContext.builder()
                .lockProvider(context.provider())
                .options(context.options())
                .plan(context.fencingPlan())
                .lease(lease)
                .waitDuration(context.waitDuration())
                .build();
        FencingTokenFlow flow = fencingTokenFlowRegistry.getRequired(context.fencingPlan().mode());
        FencingCompletion completion = flow.complete(fencingContext);
        if (!completion.isSuccess()) {
            return completion.requireFailureResult();
        }

        DefaultLockHandle handle = lockHandleFactory.create(context.provider(), completion.requireLease(), context.options());
        return LockResult.acquired(handle, context.waitDuration());
    }
}
