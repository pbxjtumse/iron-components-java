package com.xjtu.iron.reliable.task.api.repository.create;

/** Repository 创建结果。 */
public enum ReliableTaskCreateResult {

    /** 任务记录已成功创建。 */
    CREATED,

    /** 同一任务唯一键已存在，未重复创建。 */
    ALREADY_EXISTS
}
