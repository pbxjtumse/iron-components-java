package com.xjtu.iron.concurrency.api.execution.registry;

import com.xjtu.iron.concurrency.api.enums.task.AsyncTaskStatus;
import com.xjtu.iron.concurrency.api.error.AsyncError;
import com.xjtu.iron.concurrency.api.event.TaskExecutionEvent;
import com.xjtu.iron.concurrency.api.task.TaskExecutionMode;
import com.xjtu.iron.concurrency.api.task.TaskMetadata;
import com.xjtu.iron.concurrency.api.task.TaskResultMode;
import com.xjtu.iron.concurrency.api.task.TaskTimingSnapshot;

/**
 * 任务执行状态快照。
 *
 * <p>
 * 保存某个 taskId 最近一次可查询状态，适合管理接口、诊断页面、Redis 或数据库持久化。
 * 与 {@link TaskExecutionEvent} 不同，本对象不会保存原始 Throwable。
 * </p>
 */
public class TaskExecutionSnapshot {

    /**
     * 任务基础元数据。
     */
    private TaskMetadata task;

    /**
     * 当前任务状态。
     */
    private AsyncTaskStatus status;

    /**
     * 任务结果模式。
     */
    private TaskResultMode resultMode;

    /**
     * 原始任务实际执行方式。
     */
    private TaskExecutionMode executionMode = TaskExecutionMode.UNASSIGNED;

    /**
     * 时间和耗时信息。
     */
    private TaskTimingSnapshot timing = TaskTimingSnapshot.empty();

    /**
     * 不包含原始 Throwable 的结构化错误信息。
     */
    private AsyncError error = AsyncError.none();

    /**
     * 快照最近更新时间，毫秒时间戳。
     */
    private long updatedAtMillis;

    /**
     * 根据生命周期事件创建可序列化快照。
     *
     * @param event 任务生命周期事件
     * @return 任务状态快照
     */
    public static TaskExecutionSnapshot from(TaskExecutionEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }

        TaskExecutionSnapshot snapshot = new TaskExecutionSnapshot();
        snapshot.setTask(event.getTask());
        snapshot.setStatus(event.getStatus());
        snapshot.setResultMode(event.getResultMode());
        snapshot.setExecutionMode(event.getExecutionMode());
        snapshot.setTiming(event.getTiming());
        snapshot.setError(event.getError().copyWithoutThrowable());
        snapshot.setUpdatedAtMillis(event.getOccurredAt().toEpochMilli());
        return snapshot;
    }

    public TaskMetadata getTask() {
        return task;
    }

    public void setTask(TaskMetadata task) {
        this.task = task;
    }

    public AsyncTaskStatus getStatus() {
        return status;
    }

    public void setStatus(AsyncTaskStatus status) {
        this.status = status;
    }

    public TaskResultMode getResultMode() {
        return resultMode;
    }

    public void setResultMode(TaskResultMode resultMode) {
        this.resultMode = resultMode;
    }

    public TaskExecutionMode getExecutionMode() {
        return executionMode;
    }

    public void setExecutionMode(TaskExecutionMode executionMode) {
        this.executionMode = executionMode == null
                ? TaskExecutionMode.UNASSIGNED
                : executionMode;
    }

    public TaskTimingSnapshot getTiming() {
        return timing;
    }

    public void setTiming(TaskTimingSnapshot timing) {
        this.timing = timing == null ? TaskTimingSnapshot.empty() : timing;
    }

    /**
     * 获取结构化错误的独立副本。
     */
    public AsyncError getError() {
        return error.copyWithoutThrowable();
    }

    public void setError(AsyncError error) {
        this.error = error == null ? AsyncError.none() : error.copyWithoutThrowable();
    }

    public long getUpdatedAtMillis() {
        return updatedAtMillis;
    }

    public void setUpdatedAtMillis(long updatedAtMillis) {
        this.updatedAtMillis = updatedAtMillis;
    }

    /**
     * 便捷获取任务 ID。
     */
    public String getTaskId() {
        return task == null ? null : task.getTaskId();
    }

    /**
     * 便捷获取提交时间。
     */
    public long getSubmitTimeMillis() {
        return timing == null ? 0L : timing.getSubmitTimeMillis();
    }
}
