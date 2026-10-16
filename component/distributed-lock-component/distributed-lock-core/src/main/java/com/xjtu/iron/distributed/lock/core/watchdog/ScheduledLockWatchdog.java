package com.xjtu.iron.distributed.lock.core.watchdog;

import com.xjtu.iron.distributed.lock.api.model.LockAutoRenewMode;
import com.xjtu.iron.distributed.lock.api.model.LockOptions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 统一 watchdog 调度器，负责获取锁之后的租约生命周期治理。
 *
 * <p>watchdog 不是“锁本身”，而是本地后台任务。它解决的是：业务 callback 比 leaseTime 更长时，如何继续维持或检测本次 lease。不同 Provider
 * 的续期模型不同，因此通过 {@link LockAutoRenewMode} 区分：</p>
 *
 * <ul>
 *     <li>{@code CORE_MANAGED}：自研 Redis Lua。Core 定时调用 {@code handle.renew()}，由 renew.lua 做 ownerToken 比较和 PEXPIRE。</li>
 *     <li>{@code PROVIDER_MANAGED}：Redisson。真正 TTL 延长由 Redisson 内部 watchdog 完成，Core 只周期性 checkHeld，并负责 maxRenewTime 上限。</li>
 *     <li>{@code UNSUPPORTED}：Provider 不支持自动续期，start 时直接返回。</li>
 * </ul>
 */
public final class ScheduledLockWatchdog implements LockWatchdog, AutoCloseable {

    private final ScheduledExecutorService scheduler;
    private final Clock clock;
    private final Map<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

    public ScheduledLockWatchdog() {
        this(Executors.newScheduledThreadPool(1, new WatchdogThreadFactory()), Clock.systemUTC());
    }

    public ScheduledLockWatchdog(Clock clock) {
        this(Executors.newScheduledThreadPool(1, new WatchdogThreadFactory()), clock);
    }

    public ScheduledLockWatchdog(ScheduledExecutorService scheduler) {
        this(scheduler, Clock.systemUTC());
    }

    public ScheduledLockWatchdog(ScheduledExecutorService scheduler, Clock clock) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 启动或重启某个 Handle 的 watchdog。watchdogId 由 providerName + lockKey + ownerToken 组成，保证同一 lease 只有一个本地任务。
     */
    @Override
    public void start(WatchdogLockHandle handle, LockOptions options) {
        Objects.requireNonNull(handle, "handle must not be null");
        Objects.requireNonNull(options, "options must not be null");
        if (!options.isAutoRenew() || handle.autoRenewMode() == LockAutoRenewMode.UNSUPPORTED) {
            return;
        }

        stop(handle);
        Duration interval = options.getRenewInterval();
        Instant maxRenewDeadline = handle.acquiredAt().plus(options.getMaxRenewTime());
        ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(() -> tick(handle, maxRenewDeadline), interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
        tasks.put(handle.watchdogId(), future);
    }

    @Override
    public void stop(WatchdogLockHandle handle) {
        if (handle == null) {
            return;
        }
        ScheduledFuture<?> future = tasks.remove(handle.watchdogId());
        if (future != null) {
            future.cancel(false);
        }
    }

    /**
     * 单次 watchdog tick。这里最重要的规则是：maxRenewTime 到期一定先把本地 handle 标记 lost，避免长时间业务最终被解释为 SUCCESS。
     */
    private void tick(WatchdogLockHandle handle, Instant maxRenewDeadline) {
        if (handle.isReleaseAttempted() || handle.isLost()) {
            stop(handle);
            return;
        }

        if (!Instant.now(clock).isBefore(maxRenewDeadline)) {
            handle.markLostByWatchdog("maxRenewTime exceeded", null);
            if (handle.autoRenewMode() == LockAutoRenewMode.PROVIDER_MANAGED) {
                // Redisson 自己会继续 watchdog；为了真正停掉 provider-managed 续期，需要主动 unlock。
                handle.unlock();
            }
            stop(handle);
            return;
        }

        if (handle.autoRenewMode() == LockAutoRenewMode.CORE_MANAGED) {
            boolean renewed = handle.renew();
            if (!renewed && handle.isLost()) {
                stop(handle);
            }
            return;
        }

        if (handle.autoRenewMode() == LockAutoRenewMode.PROVIDER_MANAGED) {
            boolean held = handle.checkHeld();
            if (!held && handle.isLost()) {
                stop(handle);
            }
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }

    private static final class WatchdogThreadFactory implements ThreadFactory {
        private final AtomicInteger index = new AtomicInteger();
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "iron-lock-watchdog-" + index.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
