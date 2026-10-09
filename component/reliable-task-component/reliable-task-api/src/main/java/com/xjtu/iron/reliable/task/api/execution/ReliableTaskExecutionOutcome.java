package com.xjtu.iron.reliable.task.api.execution;

/** Handler 对本轮执行结果的业务分类。 */
public enum ReliableTaskExecutionOutcome {
    SUCCESS,
    RETRY,
    RECONCILE,
    MANUAL,
    DEAD
}
