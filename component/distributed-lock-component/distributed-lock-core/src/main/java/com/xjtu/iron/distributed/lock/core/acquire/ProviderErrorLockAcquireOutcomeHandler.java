package com.xjtu.iron.distributed.lock.core.acquire;

import com.xjtu.iron.distributed.lock.api.exception.LockProviderException;
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

/** PROVIDER_ERROR 状态处理器。 */
public final class ProviderErrorLockAcquireOutcomeHandler implements LockAcquireOutcomeHandler {

    private final LockEventPublisher eventPublisher;
    private final LockEventFactory eventFactory;
    private final LockMetricsFacade metricsFacade;

    public ProviderErrorLockAcquireOutcomeHandler(LockEventPublisher eventPublisher, LockEventFactory eventFactory, LockMetricsFacade metricsFacade) {
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.eventFactory = Objects.requireNonNull(eventFactory, "eventFactory must not be null");
        this.metricsFacade = Objects.requireNonNull(metricsFacade, "metricsFacade must not be null");
    }

    @Override
    public LockAcquireStatus status() {
        return LockAcquireStatus.PROVIDER_ERROR;
    }

    @Override
    public LockResult<LockHandle> handle(LockAcquireOutcomeContext context) {
        Throwable error = context.response().getError() == null
                ? new LockProviderException(context.response().getMessage())
                : context.response().getError();
        metricsFacade.recordAcquire(context.provider().providerName(), context.options().getNamespace(), context.lockName(), false,
                context.waitDuration());
        eventPublisher.publish(eventFactory.fromAcquireRequest(
                context.provider(), context.request(),
                LockEventType.PROVIDER_ERROR, LockStage.ACQUIRE, LockStatus.PROVIDER_ERROR, error));
        return LockResult.<LockHandle>builder()
                .status(LockStatus.PROVIDER_ERROR)
                .stage(LockStage.ACQUIRE)
                .acquired(false)
                .error(error)
                .lockName(context.lockName())
                .waitDuration(context.waitDuration())
                .build();
    }
}
