package com.xjtu.iron.reliable.task.api.execution;

/** Handler 对本轮执行结果的业务分类。 */
public enum ReliableTaskExecutionOutcome {

    /** 任务已成功完成，可转入成功终态。 */
    SUCCESS,

    /** 本轮失败但允许按策略延迟重试。 */
    RETRY,

    /** 结果不确定，需要先与外部系统对账。 */
    RECONCILE,

    /** 停止自动推进，等待人工处理。 */
    MANUAL,

    /** 确认不再自动执行，任务进入死信终态。 */
    DEAD
}
