package com.xjtu.iron.reliable.task.api.state;

/** Reliable Task 的持久化生命周期状态。 */
public enum ReliableTaskStatus {
    READY,
    RUNNING,
    RETRY_WAIT,
    WAIT_RECONCILE,
    MANUAL,
    SUCCEEDED,
    DEAD,
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
