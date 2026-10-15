package com.xjtu.iron.idempotent.core.observation;

/**
 * 幂等生命周期事件类型。
 *
 * <p>事件描述“发生了什么”，而 {@code IdempotencyStage} 描述“发生在哪个阶段”。</p>
 */
public enum IdempotencyEventType {
    ACQUIRE_ATTEMPT,
    ACQUIRED,
    REPLAYED,
    PROCESSING,
    PROCESSING_EXPIRED,
    PREVIOUS_FAILED,
    KEY_CONFLICT,
    LOCK_FALLBACK,
    RECOVERY_ATTEMPT,
    RECOVERY_ACQUIRED,
    RECOVERY_REJECTED,
    EXECUTION_STARTED,
    EXECUTION_SUCCESS,
    EXECUTION_FAILED,
    TRANSACTION_FAILED,
    TRANSACTION_COMMIT_UNKNOWN,
    OWNERSHIP_LOST,
    REPOSITORY_ERROR
}
