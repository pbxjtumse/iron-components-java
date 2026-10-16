package com.xjtu.iron.concurrency.core.async;

import com.xjtu.iron.concurrency.api.execution.executor.AsyncExecutor;
import com.xjtu.iron.concurrency.api.execution.task.AsyncTask;
import com.xjtu.iron.concurrency.api.execution.task.TaskCancelResult;
import com.xjtu.iron.concurrency.api.execution.task.TaskCancellationManager;
import com.xjtu.iron.concurrency.api.execution.task.TaskHandle;
import com.xjtu.iron.concurrency.core.spi.TaskExecutionTemplate;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * 默认异步执行器。
 *
 * <p>该类只作为业务入口，实际投递流程交给 {@link TaskExecutionTemplate}。</p>
 */
public final class DefaultAsyncExecutor implements AsyncExecutor {

    /** 任务投递模板。 */
    private final TaskExecutionTemplate taskExecutionTemplate;

    /** 当前节点任务取消管理器。 */
    private final TaskCancellationManager cancellationManager;

    public DefaultAsyncExecutor(TaskExecutionTemplate taskExecutionTemplate, TaskCancellationManager cancellationManager) {
        this.taskExecutionTemplate = Objects.requireNonNull(
                taskExecutionTemplate,
                "taskExecutionTemplate must not be null"
        );
        this.cancellationManager = Objects.requireNonNull(
                cancellationManager,
                "cancellationManager must not be null"
        );
    }

    @Override
    public void execute(String executorName, String taskName, Runnable runnable) {
        taskExecutionTemplate.execute(executorName, taskName, runnable);
    }

    @Override
    public boolean tryExecute(String executorName, String taskName, Runnable runnable) {
        return taskExecutionTemplate.tryExecute(executorName, taskName, runnable);
    }

    @Override
    public <T> CompletableFuture<T> supply(String executorName, String taskName, Supplier<T> supplier) {
        return taskExecutionTemplate.supply(executorName, taskName, supplier);
    }

    @Override
    public CompletableFuture<Void> run(String executorName, String taskName, Runnable runnable) {
        return taskExecutionTemplate.run(executorName, taskName, runnable);
    }

    @Override
    public <T> CompletableFuture<T> submit(AsyncTask<T> task) {
        return taskExecutionTemplate.submit(task);
    }

    @Override
    public <T> TaskHandle<T> submitHandle(AsyncTask<T> task) {
        return taskExecutionTemplate.submitHandle(task);
    }

    @Override
    public TaskCancelResult cancel(String taskId, boolean mayInterruptIfRunning) {
        return cancellationManager.cancel(taskId, mayInterruptIfRunning);
    }
}
