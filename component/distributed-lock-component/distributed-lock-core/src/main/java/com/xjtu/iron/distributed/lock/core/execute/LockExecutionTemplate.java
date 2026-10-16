package com.xjtu.iron.distributed.lock.core.execute;

import com.xjtu.iron.distributed.lock.api.client.LockCallback;
import com.xjtu.iron.distributed.lock.api.exception.FencingTokenRejectedException;
import com.xjtu.iron.distributed.lock.api.exception.LockLostException;
import com.xjtu.iron.distributed.lock.api.model.LockHandle;
import com.xjtu.iron.distributed.lock.api.model.LockOptions;
import com.xjtu.iron.distributed.lock.api.model.LockResult;
import com.xjtu.iron.distributed.lock.api.status.LockStage;
import com.xjtu.iron.distributed.lock.api.status.LockStatus;
import com.xjtu.iron.distributed.lock.core.acquire.LockAcquisitionService;
import com.xjtu.iron.distributed.lock.core.client.DefaultLockHandle;
import com.xjtu.iron.distributed.lock.core.observability.LockEventFactory;
import com.xjtu.iron.distributed.lock.core.observability.LockEventPublisher;
import com.xjtu.iron.distributed.lock.core.observability.LockEventType;
import com.xjtu.iron.distributed.lock.core.observability.LockMetricsFacade;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 持锁执行业务模板。
 *
 * <p>本类是 execute 用例的编排者，核心步骤固定为：先 acquire；未获取则直接复制失败结果；获取成功后执行业务 callback；无论业务成功还是失败，
 * 最后都释放锁并用 {@link LockResultResolver} 统一解释最终状态。</p>
 *
 * <p>watchdog 不在这里启动。它在 acquire 成功创建 {@link DefaultLockHandle} 时启动，在 handle release 时停止。这样手工 tryLock 与 execute
 * 共享同一套租约生命周期，execute 只关心 callback、release 和结果归并。</p>
 */
public final class LockExecutionTemplate {

    private final LockAcquisitionService acquisitionService;
    private final LockEventPublisher eventPublisher;
    private final LockEventFactory eventFactory;
    private final LockMetricsFacade metricsFacade;
    private final Clock clock;
    private final LockResultResolver resultResolver;

    public LockExecutionTemplate(LockAcquisitionService acquisitionService, LockEventPublisher eventPublisher, LockEventFactory eventFactory,
            LockMetricsFacade metricsFacade, Clock clock, LockResultResolver resultResolver) {
        this.acquisitionService = Objects.requireNonNull(acquisitionService, "acquisitionService must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.eventFactory = Objects.requireNonNull(eventFactory, "eventFactory must not be null");
        this.metricsFacade = Objects.requireNonNull(metricsFacade, "metricsFacade must not be null");
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.resultResolver = Objects.requireNonNull(resultResolver, "resultResolver must not be null");
    }

    public <T> LockResult<T> execute(String lockName, LockOptions options, LockCallback<T> callback) {
        Objects.requireNonNull(callback, "callback must not be null");

        LockAcquisitionService.AcquireAttempt attempt = acquisitionService.acquire(lockName, options);
        LockResult<LockHandle> acquireResult = attempt.result();
        if (acquireResult.status() != LockStatus.ACQUIRED) {
            return copyAcquireFailure(acquireResult);
        }

        DefaultLockHandle handle = requireDefaultHandle(acquireResult);
        LockOptions actualOptions = attempt.requireOptions();
        ExecutionOutcome<T> executionOutcome = invokeCallback(lockName, handle, actualOptions, callback);
        LockReleaseOutcome releaseOutcome = handle.releaseWithOutcome();

        Duration holdDuration = Duration.between(handle.acquiredAt(), Instant.now(clock));
        LockResult<T> result = resultResolver.resolve(executionOutcome, releaseOutcome, handle, acquireResult.waitDuration(), holdDuration, actualOptions);

        metricsFacade.recordHold(handle.lease(), result.status(), holdDuration);
        if (result.status() == LockStatus.SUCCESS) {
            eventPublisher.publish(eventFactory.fromLease(handle.lease(), LockEventType.EXECUTION_SUCCESS, LockStage.EXECUTE, LockStatus.SUCCESS, null));
        }
        return result;
    }

    private <T> ExecutionOutcome<T> invokeCallback(String lockName, DefaultLockHandle handle, LockOptions options, LockCallback<T> callback) {
        try {
            eventPublisher.publish(eventFactory.fromLease(handle.lease(), LockEventType.EXECUTION_STARTED, LockStage.EXECUTE, null, null));
            T value = callback.doWithLock(handle);

            // callback 没抛异常不代表锁一定仍然有效。watchdog 或业务主动 check 可能已经把 handle 标记为 lost。
            if (handle.isLost() && options.isFailOnLockLost()) {
                return ExecutionOutcome.failure(LockStatus.LOCK_LOST, LockStage.RENEW, new LockLostException("lock lost during execution: " + lockName));
            }
            return ExecutionOutcome.success(value);
        } catch (FencingTokenRejectedException error) {
            eventPublisher.publish(eventFactory.fromLease(handle.lease(), LockEventType.FENCING_REJECTED, LockStage.FENCING, LockStatus.FENCING_REJECTED, error));
            return ExecutionOutcome.failure(LockStatus.FENCING_REJECTED, LockStage.FENCING, error);
        } catch (LockLostException error) {
            eventPublisher.publish(eventFactory.fromLease(handle.lease(), LockEventType.LOCK_LOST, LockStage.CHECK, LockStatus.LOCK_LOST, error));
            return ExecutionOutcome.failure(LockStatus.LOCK_LOST, LockStage.CHECK, error);
        } catch (Throwable error) {
            eventPublisher.publish(eventFactory.fromLease(handle.lease(), LockEventType.EXECUTION_FAILED, LockStage.EXECUTE, LockStatus.EXECUTION_FAILED, error));
            return ExecutionOutcome.failure(LockStatus.EXECUTION_FAILED, LockStage.EXECUTE, error);
        }
    }

    private DefaultLockHandle requireDefaultHandle(LockResult<LockHandle> acquireResult) {
        LockHandle handle = acquireResult.handle().orElseThrow(() -> new IllegalStateException("acquired result has no handle"));
        if (!(handle instanceof DefaultLockHandle)) {
            throw new IllegalStateException("acquired handle is not managed by DefaultDistributedLockClient: " + handle.getClass().getName());
        }
        return (DefaultLockHandle) handle;
    }

    private <T> LockResult<T> copyAcquireFailure(LockResult<LockHandle> acquireResult) {
        return LockResult.<T>builder().status(acquireResult.status()).stage(acquireResult.stage()).acquired(acquireResult.acquired())
                .error(acquireResult.error().orElse(null)).lockName(acquireResult.lockName()).lockKey(acquireResult.lockKey())
                .ownerToken(acquireResult.ownerToken()).fencingToken(acquireResult.fencingToken().orElse(null))
                .fencingTokenProviderName(acquireResult.fencingTokenProviderName().orElse(null)).waitDuration(acquireResult.waitDuration()).build();
    }
}
