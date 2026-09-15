package com.xjtu.iron.distributed.lock.core.watchdog;

import com.xjtu.iron.distributed.lock.api.model.LockAutoRenewMode;
import com.xjtu.iron.distributed.lock.api.model.LockHandle;

/**
 * watchdog 可操作的锁句柄视图。
 *
 * <p>watchdog 不需要理解底层 Provider，只需要周期性调用 {@link #renew()}，并在达到最大续期时间或确定失锁时
 * 标记当前 handle 已失效。</p>
 */
public interface WatchdogLockHandle extends LockHandle {

    /**
     * watchdog 任务唯一标识。
     *
     * @return 用于注册和取消续期任务的唯一 id。
     */
    String watchdogId();

    /**
     * 当前 Provider 的自动续期模式。
     * CORE_MANAGED 时 watchdog 调 renew；PROVIDER_MANAGED 时 watchdog 只做存活监视和 maxRenewTime 边界控制。
     */
    LockAutoRenewMode autoRenewMode();

    /**
     * 由 watchdog 将当前 handle 标记为失锁。
     *
     * @param reason 失锁原因。
     * @param error  异常信息，可为空。
     */
    void markLostByWatchdog(String reason, Throwable error);
}
