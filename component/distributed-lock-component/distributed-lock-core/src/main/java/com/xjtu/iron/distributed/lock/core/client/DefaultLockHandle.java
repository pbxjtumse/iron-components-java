package com.xjtu.iron.distributed.lock.core.client;

import com.xjtu.iron.distributed.lock.api.exception.LockLostException;
import com.xjtu.iron.distributed.lock.api.model.LockAutoRenewMode;
import com.xjtu.iron.distributed.lock.api.status.LockStage;
import com.xjtu.iron.distributed.lock.api.status.LockStatus;
import com.xjtu.iron.distributed.lock.core.execute.LockReleaseOutcome;
import com.xjtu.iron.distributed.lock.core.observability.LockEventFactory;
import com.xjtu.iron.distributed.lock.core.observability.LockEventPublisher;
import com.xjtu.iron.distributed.lock.core.observability.LockEventType;
import com.xjtu.iron.distributed.lock.core.observability.LockMetricsFacade;
import com.xjtu.iron.distributed.lock.core.watchdog.LockWatchdog;
import com.xjtu.iron.distributed.lock.core.watchdog.WatchdogLockHandle;
import com.xjtu.iron.distributed.lock.spi.LockProvider;
import com.xjtu.iron.distributed.lock.spi.protocol.check.LockCheckRequest;
import com.xjtu.iron.distributed.lock.spi.protocol.check.LockCheckResponse;
import com.xjtu.iron.distributed.lock.spi.protocol.common.LockLease;
import com.xjtu.iron.distributed.lock.spi.protocol.release.LockReleaseRequest;
import com.xjtu.iron.distributed.lock.spi.protocol.release.LockReleaseResponse;
import com.xjtu.iron.distributed.lock.spi.protocol.renew.LockRenewRequest;
import com.xjtu.iron.distributed.lock.spi.protocol.renew.LockRenewResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * 一次成功加锁后的本地句柄。
 *
 * <p>Handle 的职责不是“重新实现锁”，而是把一次 {@link LockLease} 变成可操作对象。它向外提供 check/renew/unlock/assertHeld，向内承载
 * watchdog 需要的 owner 信息、运行态标记、事件和指标。{@link LockLease} 是不可变事实，{@link LockRuntimeState} 是本地运行态。</p>
 *
 * <p>本组件的 owner 语义是 ownerToken，不是 Java Thread。因此同一个 Handle 可以跨线程传递，Provider 需要自己把 ownerToken 映射到底层语义；
 * Redisson Provider 里的 {@code RedissonOwnershipRegistry} 就是为了解决 Redisson threadId owner 与本组件 ownerToken 之间的差异。</p>
 */
public final class DefaultLockHandle implements WatchdogLockHandle {

    private final LockProvider provider;
    private final LockLease lease;
    private final LockRuntimeState runtimeState;
    private final LockEventPublisher eventPublisher;
    private final LockEventFactory eventFactory;
    private final LockMetricsFacade metricsFacade;
    private final LockWatchdog watchdog;

    /** 兼容老测试或手工构造场景；生产默认通过 {@link LockHandleFactory} 注入 watchdog。 */
    public DefaultLockHandle(LockProvider provider, LockLease lease, LockRuntimeState runtimeState, LockEventPublisher eventPublisher,
            LockEventFactory eventFactory, LockMetricsFacade metricsFacade) {
        this(provider, lease, runtimeState, eventPublisher, eventFactory, metricsFacade, null);
    }

    public DefaultLockHandle(LockProvider provider, LockLease lease, LockRuntimeState runtimeState, LockEventPublisher eventPublisher,
            LockEventFactory eventFactory, LockMetricsFacade metricsFacade, LockWatchdog watchdog) {
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.lease = Objects.requireNonNull(lease, "lease must not be null");
        this.runtimeState = Objects.requireNonNull(runtimeState, "runtimeState must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.eventFactory = Objects.requireNonNull(eventFactory, "eventFactory must not be null");
        this.metricsFacade = Objects.requireNonNull(metricsFacade, "metricsFacade must not be null");
        this.watchdog = watchdog;
    }

    public LockLease lease() { return lease; }
    public LockRuntimeState runtimeState() { return runtimeState; }
    @Override public String lockName() { return lease.getLockName(); }
    @Override public String lockKey() { return lease.getLockKey(); }
    @Override public String ownerToken() { return lease.getOwnerToken(); }
    @Override public OptionalLong fencingToken() { return lease.fencingToken(); }
    @Override public Optional<String> fencingTokenProviderName() { return lease.fencingTokenProviderName(); }
    @Override public Instant acquiredAt() { return lease.getAcquiredAt(); }
    @Override public Duration leaseTime() { return lease.getLeaseTime(); }
    @Override public Instant expireAt() { return lease.getExpireAt(); }
    @Override public boolean isLost() { return runtimeState.isLost(); }
    @Override public boolean isReleaseAttempted() { return runtimeState.isReleaseAttempted(); }

    /**
     * 轻量检查当前 ownerToken 是否仍持有锁。NOT_FOUND/NOT_OWNER 都会把本地 Handle 标记为 lost；Provider error 只发布错误，不把它误判为确定失锁。
     */
    @Override
    public boolean checkHeld() {
        if (runtimeState.isReleaseAttempted() || runtimeState.isLost()) { return false; }
        LockCheckResponse response = provider.check(LockCheckRequest.fromLease(lease));
        switch (response.getStatus()) {
            case HELD:
                return true;
            case NOT_FOUND:
            case NOT_OWNER:
                markLost(LockStage.CHECK, null);
                return false;
            case PROVIDER_ERROR:
            default:
                publishProviderError(LockStage.CHECK, response.getError());
                return false;
        }
    }

    /**
     * 手动续期入口，仅 Provider 声明支持 manualRenew 时才应该被 Core watchdog 调用。自研 Redis Lua 支持，Redisson Provider 明确不支持手动 renew。
     */
    @Override
    public boolean renew() {
        if (runtimeState.isReleaseAttempted() || runtimeState.isLost()) { return false; }
        LockRenewResponse response = provider.renew(LockRenewRequest.fromLease(lease));
        switch (response.getStatus()) {
            case RENEWED:
                metricsFacade.recordRenew(lease, true);
                publish(LockEventType.RENEWED, LockStage.RENEW, null, null);
                return true;
            case NOT_FOUND:
            case NOT_OWNER:
                metricsFacade.recordRenew(lease, false);
                markLost(LockStage.RENEW, null);
                return false;
            case PROVIDER_ERROR:
            default:
                metricsFacade.recordRenew(lease, false);
                publishProviderError(LockStage.RENEW, response.getError());
                return false;
        }
    }

    @Override
    public boolean unlock() {
        return releaseWithOutcome().isReleased();
    }

    /**
     * 执行释放并返回 Core 内部解释结果。方法保证幂等：同一个 Handle 重复释放只会有一次真正 Provider release。
     */
    public LockReleaseOutcome releaseWithOutcome() {
        if (!runtimeState.markReleaseAttemptedOnce()) {
            stopWatchdog();
            return LockReleaseOutcome.alreadyAttempted();
        }
        try {
            LockReleaseResponse response = provider.release(LockReleaseRequest.fromLease(lease));
            LockReleaseOutcome outcome = LockReleaseOutcome.fromProviderResponse(response);
            switch (outcome.getType()) {
                case RELEASED:
                    metricsFacade.recordRelease(lease, true);
                    publish(LockEventType.RELEASED, LockStage.RELEASE, null, null);
                    break;
                case LOCK_LOST:
                    metricsFacade.recordRelease(lease, false);
                    markLost(LockStage.RELEASE, null);
                    break;
                case RELEASE_FAILED:
                    metricsFacade.recordRelease(lease, false);
                    publish(LockEventType.RELEASE_FAILED, LockStage.RELEASE, LockStatus.RELEASE_FAILED, outcome.getError());
                    break;
                case ALREADY_ATTEMPTED:
                default:
                    break;
            }
            return outcome;
        } finally {
            // 手工 tryLock 也可能启动 watchdog，因此释放时必须立即清理任务，不能等下一个调度 tick。
            stopWatchdog();
        }
    }

    @Override
    public void assertHeld() {
        if (!checkHeld()) { throw new LockLostException("lock lost: " + lease.getLockName()); }
    }

    @Override
    public LockAutoRenewMode autoRenewMode() {
        return provider.capabilities().getAutoRenewMode();
    }

    @Override
    public String watchdogId() {
        return lease.getProviderName() + ':' + lease.getLockKey() + ':' + lease.getOwnerToken();
    }

    @Override
    public void markLostByWatchdog(String reason, Throwable error) {
        if (runtimeState.markLostOnce()) {
            metricsFacade.recordLost(lease);
            publish(LockEventType.LOCK_LOST, LockStage.RENEW, LockStatus.LOCK_LOST, error);
        }
    }

    private void stopWatchdog() {
        if (watchdog != null) {
            watchdog.stop(this);
        }
    }

    private void markLost(LockStage stage, Throwable error) {
        if (runtimeState.markLostOnce()) {
            metricsFacade.recordLost(lease);
            publish(LockEventType.LOCK_LOST, stage, LockStatus.LOCK_LOST, error);
        }
    }

    private void publishProviderError(LockStage stage, Throwable error) {
        publish(LockEventType.PROVIDER_ERROR, stage, LockStatus.PROVIDER_ERROR, error);
    }

    private void publish(LockEventType type, LockStage stage, LockStatus status, Throwable error) {
        eventPublisher.publish(eventFactory.fromLease(lease, type, stage, status, error));
    }
}
