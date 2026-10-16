package com.xjtu.iron.reliable.task.api.repository.claim;

import com.xjtu.iron.reliable.task.api.model.ReliableTask;
import com.xjtu.iron.reliable.task.api.state.ReliableTaskStatus;

import java.util.Objects;

/** CAS 抢占结果。 */
public final class ReliableTaskClaimResult {

    /** 当前 owner 是否成功获得执行权。 */
    private final boolean claimed;

    /** 抢占成功后的 RUNNING 任务快照；抢占失败时为空。 */
    private final ReliableTask task;

    /** 本次抢占前候选任务所处的状态；抢占失败时为空。 */
    private final ReliableTaskStatus claimedFromStatus;

    public ReliableTaskClaimResult(
            boolean claimed,
            ReliableTask task,
            ReliableTaskStatus claimedFromStatus) {
        this.claimed = claimed;
        this.task = task;
        this.claimedFromStatus = claimedFromStatus;
    }

    public static ReliableTaskClaimResult claimed(ReliableTask task, ReliableTaskStatus fromStatus) {
        return new ReliableTaskClaimResult(true, task, fromStatus);
    }

    public static ReliableTaskClaimResult missed() {
        return new ReliableTaskClaimResult(false, null, null);
    }

    public boolean isClaimed() {
        return claimed;
    }

    public ReliableTask getTask() {
        return task;
    }

    public ReliableTaskStatus getClaimedFromStatus() {
        return claimedFromStatus;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ReliableTaskClaimResult that)) {
            return false;
        }
        return claimed == that.claimed
                && Objects.equals(task, that.task)
                && claimedFromStatus == that.claimedFromStatus;
    }

    @Override
    public int hashCode() {
        return Objects.hash(claimed, task, claimedFromStatus);
    }

    @Override
    public String toString() {
        return "ReliableTaskClaimResult{" +
                "claimed=" + claimed +
                ", task=" + task +
                ", claimedFromStatus=" + claimedFromStatus +
                '}';
    }
}
