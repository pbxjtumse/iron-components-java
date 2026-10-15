package com.xjtu.iron.transaction.api.event;

/**
 * 一期最小事务事件类型。
 */
public enum TransactionEventType {

    /** 事务执行已启动。 */
    STARTED,

    /** 事务执行已正常完成。 */
    COMPLETED,

    /** 事务内的业务回调失败。 */
    BUSINESS_FAILED,

    /** 开启、提交、回滚等事务基础设施操作失败。 */
    INFRASTRUCTURE_FAILED
}
