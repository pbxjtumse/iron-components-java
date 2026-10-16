package com.xjtu.iron.concurrency.core.control;

import com.xjtu.iron.concurrency.api.execution.task.TaskCancelResult;
import com.xjtu.iron.concurrency.api.execution.task.TaskCancellationManager;

import java.util.Objects;

/**
 * 默认本地任务取消管理器。
 */
public final class DefaultTaskCancellationManager
        implements TaskCancellationManager {

    /**
     * 当前节点运行任务控制注册表。
     */
    private final TaskControlRegistry controlRegistry;

    public DefaultTaskCancellationManager(
            TaskControlRegistry controlRegistry
    ) {
        this.controlRegistry = Objects.requireNonNull(
                controlRegistry,
                "controlRegistry must not be null"
        );
    }

    @Override
    public TaskCancelResult cancel(String taskId, boolean mayInterruptIfRunning) {
        return controlRegistry.get(taskId)
                .map(task -> task.cancel(mayInterruptIfRunning))
                .orElse(TaskCancelResult.NOT_FOUND);
    }

    @Override
    public boolean isCancellable(String taskId) {
        return controlRegistry.get(taskId).isPresent();
    }
}
