package com.xjtu.iron.reliable.task.api.operation.submit;

/** 任务提交操作的结果。 */
public enum ReliableTaskSubmitStatus {

    /** 新任务已成功持久化。 */
    CREATED,

    /** 同一业务任务已存在，本次提交未重复创建。 */
    ALREADY_EXISTS
}
