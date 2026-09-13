package com.xjtu.iron.distributed.lock.core.fencing.flow;

import com.xjtu.iron.distributed.lock.api.exception.LockLostException;
import com.xjtu.iron.distributed.lock.api.exception.LockProviderException;
import com.xjtu.iron.distributed.lock.api.model.LockHandle;
import com.xjtu.iron.distributed.lock.api.model.LockResult;
import com.xjtu.iron.distributed.lock.api.status.LockStage;
import com.xjtu.iron.distributed.lock.api.status.LockStatus;
import com.xjtu.iron.distributed.lock.core.fencing.coordinator.FencingTokenCoordinator;
import com.xjtu.iron.distributed.lock.core.observability.LockEventFactory;
import com.xjtu.iron.distributed.lock.core.observability.LockEventPublisher;
import com.xjtu.iron.distributed.lock.core.observability.LockEventType;
import com.xjtu.iron.distributed.lock.core.observability.LockMetricsFacade;
import com.xjtu.iron.distributed.lock.spi.LockProvider;
import com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenResponse;
import com.xjtu.iron.distributed.lock.spi.protocol.check.LockCheckRequest;
import com.xjtu.iron.distributed.lock.spi.protocol.check.LockCheckResponse;
import com.xjtu.iron.distributed.lock.spi.protocol.common.LockLease;
import com.xjtu.iron.distributed.lock.spi.protocol.release.LockReleaseRequest;
import com.xjtu.iron.distributed.lock.spi.protocol.release.LockReleaseResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * fencing token flow 的公共支撑逻辑。
 *
 * <p>Flow 类只负责流程分支；公共的事件、指标、失败映射、准备阶段释放锁逻辑集中放在这里，
 * 避免 DefaultDistributedLockClient 继续膨胀。</p>
 */
public final class FencingTokenFlowSupport {

    private final FencingTokenCoordinator coordinator;
    private final LockEventPublisher eventPublisher;
    private final LockEventFactory eventFactory;
    private final LockMetricsFacade metricsFacade;
    private final Clock clock;

    public FencingTokenFlowSupport(FencingTokenCoordinator coordinator, LockEventPublisher eventPublisher, LockEventFactory eventFactory,
            LockMetricsFacade metricsFacade, Clock clock) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.eventFactory = Objects.requireNonNull(eventFactory, "eventFactory must not be null");
        this.metricsFacade = Objects.requireNonNull(metricsFacade, "metricsFacade must not be null");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public FencingTokenResponse issueExternal(FencingContext context) {
        return coordinator.issueExternal(context.plan(), context.lease(), context.options());
    }

    public Instant now() {
        return Instant.now(clock);
    }

    public void recordFencingSuccess(LockProvider lockProvider, LockLease lease, String fencingProviderName, Duration duration) {
        metricsFacade.recordFencing(lockProvider.providerName(), fencingProviderName, lease.getNamespace(), true,
                duration == null ? Duration.ZERO : duration);
        eventPublisher.publish(eventFactory.fromLease(lease, LockEventType.FENCING_TOKEN_ISSUED, LockStage.FENCING, null, null));
    }

    public LockResult<LockHandle> fencingFailure(LockProvider lockProvider, LockLease lease, Duration waitDuration, String fencingProviderName,
            FencingTokenResponse tokenResponse, Duration fencingDuration) {
        Throwable error = tokenResponse.getError();
        if (error == null) {
            error = new LockProviderException(tokenResponse.getMessage() == null ? "failed to issue fencing token" : tokenResponse.getMessage());
        }

        releaseAfterPreparationFailure(lockProvider, lease, error);

        metricsFacade.recordFencing(lockProvider.providerName(), fencingProviderName, lease.getNamespace(), false,
                fencingDuration == null ? Duration.ZERO : fencingDuration);
        eventPublisher.publish(eventFactory.fromFencing(lease,
                LockEventType.FENCING_TOKEN_FAILED, LockStatus.PROVIDER_ERROR,
                fencingProviderName, error));

        return LockResult.<LockHandle>builder()
                .status(LockStatus.PROVIDER_ERROR)
                .stage(LockStage.FENCING)
                .acquired(true)
                .error(error)
                .lockName(lease.getLockName())
                .lockKey(lease.getLockKey())
                .ownerToken(lease.getOwnerToken())
                .fencingTokenProviderName(fencingProviderName)
                .waitDuration(waitDuration)
                .build();
    }

    /**
     * 外部 fencing token 发号完成后重新确认锁归属。
     *
     * @return null 表示仍然持锁；否则返回不允许进入 callback 的最终结果。
     */
    public LockResult<LockHandle> verifyOwnershipAfterExternalFencing(LockProvider lockProvider, LockLease lease, Duration waitDuration) {
        LockCheckResponse response;
        try {
            response = lockProvider.check(LockCheckRequest.fromLease(lease));
        } catch (Throwable error) {
            releaseAfterPreparationFailure(lockProvider, lease, error);
            eventPublisher.publish(eventFactory.fromLease(lease, LockEventType.PROVIDER_ERROR, LockStage.CHECK, LockStatus.PROVIDER_ERROR, error));
            return preparationCheckFailure(lease, waitDuration, error);
        }

        if (response.isHeld()) {
            return null;
        }
        if (response.isLockLost()) {
            metricsFacade.recordLost(lease);
            eventPublisher.publish(eventFactory.fromLease(lease, LockEventType.LOCK_LOST, LockStage.CHECK, LockStatus.LOCK_LOST, null));
            return LockResult.<LockHandle>builder()
                    .status(LockStatus.LOCK_LOST)
                    .stage(LockStage.CHECK)
                    .acquired(true)
                    .error(new LockLostException("lock lost while issuing external fencing token: " + lease.getLockName()))
                    .lockName(lease.getLockName())
                    .lockKey(lease.getLockKey())
                    .ownerToken(lease.getOwnerToken())
                    .fencingToken(lease.fencingToken().orElseThrow())
                    .fencingTokenProviderName(lease.fencingTokenProviderName().orElse(null))
                    .waitDuration(waitDuration)
                    .build();
        }

        Throwable error = response.getError();
        if (error == null) {
            error = new LockProviderException(response.getMessage() == null
                    ? "failed to verify lock ownership after external fencing"
                    : response.getMessage());
        }
        releaseAfterPreparationFailure(lockProvider, lease, error);
        eventPublisher.publish(eventFactory.fromLease(lease, LockEventType.PROVIDER_ERROR, LockStage.CHECK, LockStatus.PROVIDER_ERROR, error));
        return preparationCheckFailure(lease, waitDuration, error);
    }

    private LockResult<LockHandle> preparationCheckFailure(LockLease lease, Duration waitDuration, Throwable error) {
        return LockResult.<LockHandle>builder()
                .status(LockStatus.PROVIDER_ERROR)
                .stage(LockStage.CHECK)
                .acquired(true)
                .error(error)
                .lockName(lease.getLockName())
                .lockKey(lease.getLockKey())
                .ownerToken(lease.getOwnerToken())
                .fencingToken(lease.fencingToken().isPresent() ? lease.fencingToken().getAsLong() : null)
                .fencingTokenProviderName(lease.fencingTokenProviderName().orElse(null))
                .waitDuration(waitDuration)
                .build();
    }

    /** 发号或发号后校验失败时，尽最大努力释放已经获取的锁。 */
    private void releaseAfterPreparationFailure(LockProvider lockProvider, LockLease lease, Throwable primaryError) {
        try {
            LockReleaseResponse releaseResponse = lockProvider.release(LockReleaseRequest.fromLease(lease));
            switch (releaseResponse.getStatus()) {
                case RELEASED:
                    metricsFacade.recordRelease(lease, true);
                    eventPublisher.publish(eventFactory.fromLease(lease, LockEventType.RELEASED, LockStage.RELEASE, null, null));
                    break;
                case NOT_FOUND:
                case NOT_OWNER:
                    metricsFacade.recordRelease(lease, false);
                    metricsFacade.recordLost(lease);
                    eventPublisher.publish(eventFactory.fromLease(lease, LockEventType.LOCK_LOST, LockStage.RELEASE, LockStatus.LOCK_LOST, null));
                    break;
                case PROVIDER_ERROR:
                default:
                    metricsFacade.recordRelease(lease, false);
                    Throwable releaseError = releaseResponse.getError();
                    if (releaseError == null) {
                        releaseError = new LockProviderException(
                                releaseResponse.getMessage() == null
                                        ? "failed to release lock after preparation failure"
                                        : releaseResponse.getMessage());
                    }
                    primaryError.addSuppressed(releaseError);
                    eventPublisher.publish(eventFactory.fromLease(lease,
                            LockEventType.RELEASE_FAILED, LockStage.RELEASE,
                            LockStatus.RELEASE_FAILED, releaseError));
                    break;
            }
        } catch (Throwable releaseError) {
            metricsFacade.recordRelease(lease, false);
            primaryError.addSuppressed(releaseError);
            eventPublisher.publish(eventFactory.fromLease(lease,
                    LockEventType.RELEASE_FAILED, LockStage.RELEASE,
                    LockStatus.RELEASE_FAILED, releaseError));
        }
    }
}
