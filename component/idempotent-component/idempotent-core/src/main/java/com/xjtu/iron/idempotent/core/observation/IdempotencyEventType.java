package com.xjtu.iron.idempotent.core.observation;

/**
 * 幂等生命周期事件类型。
 *
 * <p>事件描述“发生了什么”，而 {@code IdempotencyStage} 描述“发生在哪个阶段”。</p>
 */
public enum IdempotencyEventType {

    /** 开始尝试创建、抢占或重用幂等记录。 */
    ACQUIRE_ATTEMPT,

    /** 当前请求已取得幂等执行权。 */
    ACQUIRED,

    /** 命中历史成功记录并重放结果。 */
    REPLAYED,

    /** 命中仍在处理的记录。 */
    PROCESSING,

    /** 命中 lease 已过期的处理中记录。 */
    PROCESSING_EXPIRED,

    /** 命中上一次执行失败的记录。 */
    PREVIOUS_FAILED,

    /** 幂等 key 与历史请求指纹冲突。 */
    KEY_CONFLICT,

    /** 存储不具备所需能力，流程退化为锁保护。 */
    LOCK_FALLBACK,

    /** 开始尝试恢复过期的处理中记录。 */
    RECOVERY_ATTEMPT,

    /** Recovery 已取得记录的新执行权。 */
    RECOVERY_ACQUIRED,

    /** Recovery 因状态、版本或 owner 不匹配而被拒绝。 */
    RECOVERY_REJECTED,

    /** 已开始执行真实业务回调。 */
    EXECUTION_STARTED,

    /** 真实业务回调执行成功。 */
    EXECUTION_SUCCESS,

    /** 真实业务回调执行失败。 */
    EXECUTION_FAILED,

    /** 幂等与业务的本地事务执行失败。 */
    TRANSACTION_FAILED,

    /** 提交阶段异常，无法可靠确定事务最终结果。 */
    TRANSACTION_COMMIT_UNKNOWN,

    /** 当前 owner 已失去对记录的更新权。 */
    OWNERSHIP_LOST,

    /** Repository 访问或状态转换失败。 */
    REPOSITORY_ERROR
}
