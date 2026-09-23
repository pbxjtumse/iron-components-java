package com.xjtu.iron.idempotent.core.state;

import com.xjtu.iron.idempotent.api.execution.IdempotencyResultStatus;
import com.xjtu.iron.idempotent.api.repository.acquire.IdempotencyAcquireStatus;
import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryStatus;

/**
 * 默认幂等状态机：只做“Repository 原子事实 -> Core 动作”的纯映射。
 *
 * <p>这里故意不访问数据库、不执行 Lua、不加分布式锁。并发正确性已经在 Repository.tryAcquire /
 * tryRecover 中通过 UNIQUE、行锁、Lua 或 CAS 完成；StateMachine 只回答下一步是 EXECUTE、REPLAY 还是 RETURN。</p>

 * <p><b>流程阅读编号：I5：将原子事实翻译成动作。</b>编号按 I（幂等）、R（路由）、D（数据访问）分组，不表示所有分支均依次执行。</p>
 * <ul>
 *     <li>1. 输入来自 Repository 的 acquire/recovery 状态，不重新查询数据库。</li>
 *     <li>2. ACQUIRED 对应执行，SUCCESS 对应重放，其他状态按规则直接返回。</li>
 *     <li>3. 状态机只做纯决策；数据库并发竞争在 I4.2 完成，业务副作用在 I6 完成。</li>
 * </ul>
 */
public final class DefaultIdempotencyStateMachine implements IdempotencyStateMachine {

    /**
     * 普通 execute() 的状态解释。
     *
     * <p>PROCESSING_EXPIRED 和 FAILED_RETRYABLE 仍然只是 RETURN：普通请求不会偷偷升级成 Recovery。</p>
     */
    @Override
    public IdempotencyStateDecision onAcquire(IdempotencyAcquireStatus status) {
        return switch (status) {
            case ACQUIRED -> IdempotencyStateDecision.execute();
            case SUCCESS -> IdempotencyStateDecision.replay();
            case DISCARDED -> IdempotencyStateDecision.returning(IdempotencyResultStatus.PREVIOUS_DISCARDED);
            case PROCESSING_ACTIVE -> IdempotencyStateDecision.returning(IdempotencyResultStatus.PROCESSING);
            case PROCESSING_EXPIRED -> IdempotencyStateDecision.returning(IdempotencyResultStatus.PROCESSING_EXPIRED);
            case FAILED_RETRYABLE -> IdempotencyStateDecision.returning(IdempotencyResultStatus.PREVIOUS_FAILED_RETRYABLE);
            case FAILED_FINAL -> IdempotencyStateDecision.returning(IdempotencyResultStatus.PREVIOUS_FAILED_FINAL);
            case KEY_CONFLICT -> IdempotencyStateDecision.returning(IdempotencyResultStatus.KEY_CONFLICT);
            case PROVIDER_ERROR -> IdempotencyStateDecision.returning(IdempotencyResultStatus.REPOSITORY_ERROR);
        };
    }

    /**
     * 显式 recover() 的状态解释。
     *
     * <p>只有 RECOVERY_ACQUIRED 才能执行恢复业务；STALE_CANDIDATE 表示扫描快照已经过期，当前 generation
     * 已经变化，旧恢复任务必须直接退出。DISCARDED 是明确终态，同样禁止恢复。</p>
     */
    @Override
    public IdempotencyStateDecision onRecovery(IdempotencyRecoveryStatus status) {
        return switch (status) {
            case RECOVERY_ACQUIRED -> IdempotencyStateDecision.execute();
            case SUCCESS -> IdempotencyStateDecision.replay();
            case DISCARDED -> IdempotencyStateDecision.returning(IdempotencyResultStatus.PREVIOUS_DISCARDED);
            case PROCESSING_ACTIVE -> IdempotencyStateDecision.returning(IdempotencyResultStatus.PROCESSING);
            case NOT_RECOVERABLE, FAILED_FINAL, NOT_FOUND -> IdempotencyStateDecision.returning(IdempotencyResultStatus.RECOVERY_NOT_ALLOWED);
            case STALE_CANDIDATE -> IdempotencyStateDecision.returning(IdempotencyResultStatus.STALE_RECOVERY_CANDIDATE);
            case KEY_CONFLICT -> IdempotencyStateDecision.returning(IdempotencyResultStatus.KEY_CONFLICT);
            case PROVIDER_ERROR -> IdempotencyStateDecision.returning(IdempotencyResultStatus.REPOSITORY_ERROR);
        };
    }
}
