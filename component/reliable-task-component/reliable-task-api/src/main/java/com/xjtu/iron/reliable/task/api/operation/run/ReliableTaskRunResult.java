package com.xjtu.iron.reliable.task.api.operation.run;

import com.xjtu.iron.reliable.task.api.model.ReliableTask;

import java.util.Objects;

/** 一次立即尝试执行的结果。 */
public final class ReliableTaskRunResult {

    /** 本次尝试执行的处理状态。 */
    private final ReliableTaskRunStatus status;

    /** CAS 抢占成功后的 RUNNING 任务快照；未抢占时为空。 */
    private final ReliableTask claimedTask;

    /** 执行与状态迁移完成后读取到的任务快照；任务不存在时为空。 */
    private final ReliableTask finalTask;

    public ReliableTaskRunResult(
            ReliableTaskRunStatus status,
            ReliableTask claimedTask,
            ReliableTask finalTask) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.claimedTask = claimedTask;
        this.finalTask = finalTask;
    }

    public ReliableTaskRunStatus getStatus() {
        return status;
    }

    public ReliableTask getClaimedTask() {
        return claimedTask;
    }

    public ReliableTask getFinalTask() {
        return finalTask;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ReliableTaskRunResult that)) {
            return false;
        }
        return status == that.status
                && Objects.equals(claimedTask, that.claimedTask)
                && Objects.equals(finalTask, that.finalTask);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, claimedTask, finalTask);
    }

    @Override
    public String toString() {
        return "ReliableTaskRunResult{" +
                "status=" + status +
                ", claimedTask=" + claimedTask +
                ", finalTask=" + finalTask +
                '}';
    }
}
