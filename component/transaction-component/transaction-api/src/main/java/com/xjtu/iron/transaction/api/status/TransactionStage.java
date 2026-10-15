package com.xjtu.iron.transaction.api.status;

/**
 * 事务执行阶段，用于错误定位、事件和可观测性。
 */
public enum TransactionStage {

    /** 校验事务请求与参数。 */
    VALIDATE,

    /** 解析本次事务需要使用的 Provider 或物理资源。 */
    RESOLVE,

    /** 开启或加入事务。 */
    BEGIN,

    /** 执行事务内的业务回调。 */
    EXECUTE,

    /** 提交事务。 */
    COMMIT,

    /** 回滚事务。 */
    ROLLBACK,

    /** 执行事务完成后的清理与通知。 */
    COMPLETION
}
