package com.xjtu.iron.distributed.lock.api.status;



/**
 * 分布式锁操作阶段。
 *
 * <p>
 * LockStatus 描述最终结果，LockStage 描述该结果发生在哪一个生命周期阶段。
 * 例如：续期时发现 ownerToken 不匹配，最终状态是 LOCK_LOST，阶段是 RENEW。
 * </p>
 */
public enum LockStage {

    /**
     * 参数校验阶段。
     */
    VALIDATE,

    /**
     * 加锁阶段，包含 Provider 的 acquire 原子操作。
     */
    ACQUIRE,

    /**
     * 等待阶段，包含 NO_WAIT / BACKOFF 等等待逻辑。
     */
    WAIT,

    /**
     * 业务回调执行阶段。
     */
    EXECUTE,

    /**
     * fencing token 校验或业务资源拒绝阶段。
     */
    FENCING,

    /**
     * 续期阶段，包含 watchdog 或手动 renew。
     */
    RENEW,

    /**
     * 解锁释放阶段。
     */
    RELEASE,

    /**
     * 持锁检查阶段，例如 checkHeld / assertHeld。
     */
    CHECK
}
