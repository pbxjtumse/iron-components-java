package com.xjtu.iron.reliable.task.api.repository;

import com.xjtu.iron.reliable.task.api.model.ReliableTask;
import com.xjtu.iron.reliable.task.api.model.ReliableTaskStatus;

/** CAS 抢占结果。 */
public record ReliableTaskClaimResult(
        boolean claimed,
        ReliableTask task,
        ReliableTaskStatus claimedFromStatus) {

    public static ReliableTaskClaimResult claimed(ReliableTask task, ReliableTaskStatus fromStatus) {
        return new ReliableTaskClaimResult(true, task, fromStatus);
    }

    public static ReliableTaskClaimResult missed() {
        return new ReliableTaskClaimResult(false, null, null);
    }
}
