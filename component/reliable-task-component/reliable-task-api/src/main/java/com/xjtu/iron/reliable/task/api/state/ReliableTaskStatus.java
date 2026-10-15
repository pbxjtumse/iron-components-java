package com.xjtu.iron.reliable.task.api.state;

/** Reliable Task 的持久化生命周期状态。 */
public enum ReliableTaskStatus {

    /** 任务已就绪，可在到期后被扫描和抢占。 */
    READY,

    /** 任务已被某个 owner 抢占，正在 lease 保护下执行。 */
    RUNNING,

    /** 任务等待下一次重试时间。 */
    RETRY_WAIT,

    /** 外部结果不确定，等待对账执行。 */
    WAIT_RECONCILE,

    /** 已停止自动推进，等待人工决策。 */
    MANUAL,

    /** 任务已成功完成。 */
    SUCCEEDED,

    /** 任务已确认无法自动恢复。 */
    DEAD,

    /** 任务已被管理操作取消。 */
    CANCELLED;

    /** 是否已经结束自动推进。 */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == DEAD || this == CANCELLED;
    }

    /** 是否可以在到期后参与普通扫描。 */
    public boolean isScheduledCandidate() {
        return this == READY || this == RETRY_WAIT || this == WAIT_RECONCILE;
    }
}
