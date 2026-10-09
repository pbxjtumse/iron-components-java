package com.xjtu.iron.reliable.task.api.operation.admin;

import com.xjtu.iron.reliable.task.api.model.ReliableTask;

import java.util.Objects;

/** 一次人工管理操作的结果。 */
public final class ReliableTaskAdminResult {

    /** 人工操作的处理状态。 */
    private final ReliableTaskAdminStatus status;

    /** 操作完成后读取到的最新任务快照；任务不存在时为空。 */
    private final ReliableTask task;

    public ReliableTaskAdminResult(ReliableTaskAdminStatus status, ReliableTask task) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.task = task;
    }

    public ReliableTaskAdminStatus getStatus() {
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
        if (!(object instanceof ReliableTaskAdminResult that)) {
            return false;
        }
        return status == that.status && Objects.equals(task, that.task);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, task);
    }

    @Override
    public String toString() {
        return "ReliableTaskAdminResult{" +
                "status=" + status +
                ", task=" + task +
                '}';
    }
}
