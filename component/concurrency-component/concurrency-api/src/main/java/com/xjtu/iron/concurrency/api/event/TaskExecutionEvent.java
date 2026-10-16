package com.xjtu.iron.concurrency.api.event;

import com.xjtu.iron.concurrency.api.enums.task.AsyncTaskStatus;
import com.xjtu.iron.concurrency.api.error.AsyncError;
import com.xjtu.iron.concurrency.api.task.TaskExecutionMode;
import com.xjtu.iron.concurrency.api.task.TaskMetadata;
import com.xjtu.iron.concurrency.api.task.TaskResultMode;
import com.xjtu.iron.concurrency.api.task.TaskTimingSnapshot;

import java.time.Instant;

/**
 * 任务执行事件。
 *
 * <p>
 * 用于向任务监听器、任务状态注册表、指标记录器传递任务执行过程中的状态、耗时、错误和上下文信息。
 * </p>
 */

public final class TaskExecutionEvent {

    /**
     * 任务基础元数据。
     */
    private final TaskMetadata task;

    /**
     * 本次事件对应的任务状态。
     */
    private final AsyncTaskStatus status;

    /**
     * 任务结果模式。
     */
    private final TaskResultMode resultMode;

    /**
     * 原始任务实际执行方式。
     *
     * <p>
     * THREAD_POOL 表示线程池工作线程执行；CALLER_THREAD 表示 CALLER_RUNS 反压执行；
     * 任务尚未运行时通常为 UNASSIGNED。
     * </p>
     */
    private final TaskExecutionMode executionMode;

    /**
     * 截止本次事件发生时的时间和耗时快照。
     */
    private final TaskTimingSnapshot timing;

    /**
     * 结构化错误信息。
     *
     * <p>正常事件使用 {@link AsyncError#none()}。</p>
     */
    private final AsyncError error;

    /**
     * 本次事件的人类可读说明。
     */
    private final String message;

    /**
     * 事件发生时间。
     */
    private final Instant occurredAt;

    public TaskExecutionEvent(
            TaskMetadata task,
            AsyncTaskStatus status,
            TaskResultMode resultMode,
            TaskExecutionMode executionMode,
            TaskTimingSnapshot timing,
            AsyncError error,
            String message,
            Instant occurredAt
    ) {
        this.task = task;
        this.status = status;
        this.resultMode = resultMode;
        this.executionMode = executionMode == null
                ? TaskExecutionMode.UNASSIGNED
                : executionMode;
        this.timing = timing == null
                ? TaskTimingSnapshot.empty()
                : timing;
        this.error = error == null
                ? AsyncError.none()
                : error.copy();
        this.message = message;
        this.occurredAt = occurredAt == null
                ? Instant.now()
                : occurredAt;
    }

    /**
     * 创建独立事件副本。
     *
     * @return 独立事件副本
     */
    public TaskExecutionEvent copy() {
        return new TaskExecutionEvent(
                task,
                status,
                resultMode,
                executionMode,
                timing,
                error,
                message,
                occurredAt
        );
    }

    public TaskMetadata getTask() {
        return task;
    }

    public AsyncTaskStatus getStatus() {
        return status;
    }

    public TaskResultMode getResultMode() {
        return resultMode;
    }

    public TaskExecutionMode getExecutionMode() {
        return executionMode;
    }

    public TaskTimingSnapshot getTiming() {
        return timing;
    }

    public AsyncError getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    /**
     * 便捷获取任务 ID。
     */
    public String getTaskId() {
        return task == null ? null : task.getTaskId();
    }

    /**
     * 便捷获取线程池名称。
     */
    public String getExecutorName() {
        return task == null ? null : task.getExecutorName();
    }

    /**
     * 便捷获取任务名称。
     */
    public String getTaskName() {
        return task == null ? null : task.getTaskName();
    }

    /**
     * 便捷获取业务标识。
     */
    public String getBizKey() {
        return task == null ? null : task.getBizKey();
    }
}
