package com.xjtu.iron.reliable.task.api.operation.run;

/** 一次显式执行请求的结果。 */
public enum ReliableTaskRunStatus {

    /** 任务已抢占并完成一轮 Handler 执行。 */
    EXECUTED,

    /** 没有找到目标任务。 */
    NOT_FOUND,

    /** 任务当前不可抢占，因而未执行 Handler。 */
    NOT_CLAIMED,

    /** 执行结束时 owner、lease 或版本已过期，结果未被提交。 */
    STALE_COMPLETION
}
