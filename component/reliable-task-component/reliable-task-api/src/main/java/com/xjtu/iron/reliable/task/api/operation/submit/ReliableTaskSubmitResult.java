package com.xjtu.iron.reliable.task.api.operation.submit;

import com.xjtu.iron.reliable.task.api.model.ReliableTask;

import java.util.Objects;

/** 一次任务提交操作的结果。 */
public final class ReliableTaskSubmitResult {

    /** 本次提交是新建任务还是命中已有任务。 */
    private final ReliableTaskSubmitStatus status;

    /** 新建或已存在的任务快照。 */
    private final ReliableTask task;

    public ReliableTaskSubmitResult(ReliableTaskSubmitStatus status, ReliableTask task) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.task = Objects.requireNonNull(task, "task must not be null");
    }

    public ReliableTaskSubmitStatus getStatus() {
        return status;
    }

    public ReliableTask getTask() {
        return task;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ReliableTaskSubmitResult that)) {
            return false;
        }
        return status == that.status && task.equals(that.task);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, task);
    }

    @Override
    public String toString() {
        return "ReliableTaskSubmitResult{" +
                "status=" + status +
                ", task=" + task +
                '}';
    }
}
