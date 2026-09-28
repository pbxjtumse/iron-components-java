package com.xjtu.iron.idempotent.core.execution.lock;

import com.xjtu.iron.distributed.lock.api.LockWaitStrategy;
import com.xjtu.iron.distributed.lock.api.client.DistributedLockClient;
import com.xjtu.iron.distributed.lock.api.client.LockCallback;
import com.xjtu.iron.distributed.lock.api.model.LockOptions;
import com.xjtu.iron.distributed.lock.api.model.LockResult;
import com.xjtu.iron.idempotent.api.execution.*;
import com.xjtu.iron.idempotent.api.policy.IdempotencyLockOptions;
import com.xjtu.iron.idempotent.api.policy.IdempotencyPolicy;
import com.xjtu.iron.idempotent.api.repository.IdempotencyRepository;
import com.xjtu.iron.idempotent.api.repository.write.*;
import com.xjtu.iron.idempotent.api.storage.IdempotencyStorageContext;
import com.xjtu.iron.idempotent.core.observation.IdempotencyEvent;
import com.xjtu.iron.idempotent.core.observation.IdempotencyEventPublisher;
import com.xjtu.iron.idempotent.core.observation.IdempotencyEventType;

import java.time.Clock;
import java.time.Instant;

/** 仅执行 tryAcquire/tryRecover 的可选短锁与降级；不持锁执行业务回调。
 * <p><b>流程阅读编号：I3：可选短锁包装。</b>编号按 I（幂等）、R（路由）、D（数据访问）分组，不表示所有分支均依次执行。</p>
 * <ul>
 *     <li>1. 未启用锁时直接执行传入的 tryAcquire/tryRecover 操作。</li>
 *     <li>2. 启用后构造锁标识；锁不可用时根据策略拒绝或退回 Repository 原子操作。</li>
 *     <li>3. 返回 StateOperationOutcome 只说明调用及锁降级结果；执行资格仍看内部 acquire/recovery 状态。</li>
 *     <li>4. 锁只覆盖状态操作，不覆盖业务 callback；正确性不能只依赖分布式锁。</li>
 * </ul>
 */
public final class IdempotencyStateOperationExecutor {
    private final DistributedLockClient lockClient;
    private final IdempotencyEventPublisher events;
    private final Clock clock;

    public IdempotencyStateOperationExecutor(DistributedLockClient lockClient, IdempotencyEventPublisher events, Clock clock) {
        this.lockClient = lockClient;
        this.events = events;
        this.clock = clock;
    }

    /** 可选锁只包 tryAcquire/tryRecover，且 lock identity 必须包含 storeName。 */
    public <R> StateOperationOutcome<R> invoke(IdempotencyPolicy policy, IdempotencyRepository repository,
                                                           IdempotencyStorageContext storage, String routeKey, String key,
                                                           java.util.function.Supplier<R> operation) {
        IdempotencyLockOptions lock = policy.getLockOptions();
        if (!lock.isEnabled()) return StateOperationOutcome.direct(operation.get());

        if (lockClient == null) {
            if (lock.isFallbackToStateOnFailure()) {
                publish(IdempotencyEventType.LOCK_FALLBACK, IdempotencyStage.LOCK, policy, repository, null);
                return StateOperationOutcome.fallback(operation.get());
            }
            return StateOperationOutcome.lockRejected(new IllegalStateException("lock enabled but DistributedLockClient unavailable"));
        }

        LockWaitStrategy waitStrategy = lock.getWaitTime().isZero() ? LockWaitStrategy.NO_WAIT : LockWaitStrategy.BACKOFF;
        LockOptions lockOptions = LockOptions.builder()
                .namespace("idempotency:" + policy.getNamespace())
                .providerName(lock.getProviderName())
                .waitTime(lock.getWaitTime())
                .waitStrategy(waitStrategy)
                .leaseTime(lock.getLeaseTime())
                .autoRenew(false)
                .fencingRequired(false)
                .build();

        String lockName = "state:" + storage.getStoreName() + ":" + routePart(routeKey) + ":" + key;
        LockResult<R> result = lockClient.execute(lockName, lockOptions, (LockCallback<R>) handle -> operation.get());
        if (result.isSuccess() && result.value().isPresent()) return StateOperationOutcome.direct(result.value().get());
        if (lock.isFallbackToStateOnFailure()) {
            publish(IdempotencyEventType.LOCK_FALLBACK, IdempotencyStage.LOCK, policy, repository, result.error().orElse(null));
            return StateOperationOutcome.fallback(operation.get());
        }
        return StateOperationOutcome.lockRejected(result.error().orElseGet(
                () -> new IllegalStateException("distributed lock not acquired: " + result.status())));
    }

    private String routePart(String routeKey) {
        String normalized = normalize(routeKey);
        return normalized == null ? "_" : normalized;
    }

    private void publish(IdempotencyEventType type, IdempotencyStage stage, IdempotencyPolicy policy,
                         IdempotencyRepository repository, Throwable error) {
        events.publish(new IdempotencyEvent(type, stage, policy.getMode(), repository.providerName(), Instant.now(clock), error));
    }

    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }

}
