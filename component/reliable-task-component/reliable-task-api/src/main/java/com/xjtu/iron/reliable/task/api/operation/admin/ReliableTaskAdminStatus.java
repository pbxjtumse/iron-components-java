package com.xjtu.iron.reliable.task.api.operation.admin;

/** 人工管理操作的执行结果。 */
public enum ReliableTaskAdminStatus {

    /** 任务状态已按管理命令更新。 */
    UPDATED,

    /** 没有找到目标任务。 */
    NOT_FOUND,

    /** 当前状态不允许执行该管理转换。 */
    INVALID_STATE,

    /** 乐观锁版本已变更，本次管理操作被拒绝。 */
    STALE_VERSION
}
