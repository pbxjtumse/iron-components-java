package com.xjtu.iron.concurrency.core.control;

import com.xjtu.iron.concurrency.api.execution.task.TaskCancelResult;
import com.xjtu.iron.concurrency.api.execution.task.TaskCancellationManager;
import com.xjtu.iron.concurrency.api.execution.task.TaskHandle;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * 默认可控制任务句柄。
 *
 * @param <T> 任务结果类型
 */
public final class DefaultTaskHandle<T> implements TaskHandle<T> {

    /** 任务唯一 ID。 */
    private final String taskId;

    /** 最终结果 Future。 */
    private final CompletableFuture<T> future;

    /** 当前节点任务取消管理器。 */
    private final TaskCancellationManager cancellationManager;

    public DefaultTaskHandle(
            String taskId,
            CompletableFuture<T> future,
            TaskCancellationManager cancellationManager
    ) {
        this.taskId = Objects.requireNonNull(taskId, "taskId must not be null");
        this.future = Objects.requireNonNull(future, "future must not be null");
        this.cancellationManager = Objects.requireNonNull(
                cancellationManager,
                "cancellationManager must not be null"
        );
    }

    @Override
    public String getTaskId() {
        return taskId;
    }

    @Override
    public CompletableFuture<T> getFuture() {
        return future;
    }

    @Override
    public TaskCancelResult cancel(boolean mayInterruptIfRunning) {
        return cancellationManager.cancel(taskId, mayInterruptIfRunning);
    }
}
