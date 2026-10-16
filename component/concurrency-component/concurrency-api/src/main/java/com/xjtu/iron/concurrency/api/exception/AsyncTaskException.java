package com.xjtu.iron.concurrency.api.exception;

import com.xjtu.iron.concurrency.api.error.AsyncError;


/**
 * 异步任务执行异常。
 *
 * <p>
 * 当用户提交的 Runnable/Supplier 在执行过程中抛出异常时，
 * 并行组件会包装成该异常，并通过 CompletableFuture.completeExceptionally 传递给调用方。
 * </p>
 */
public class AsyncTaskException extends ConcurrencyException {

    /**
     * 线程池名称。
     */
    private final String executorName;

    /**
     * 任务名称。
     */
    private final String taskName;

    /**
     * 任务唯一 ID。
     */
    private final String taskId;

    public AsyncTaskException(
            String executorName,
            String taskName,
            String taskId,
            AsyncError error,
            Throwable cause
    ) {
        super("Async task execution failed: executor=" + executorName + ", task=" + taskName,
                error,
                cause);
        this.executorName = executorName;
        this.taskName = taskName;
        this.taskId = taskId;
    }

    public String getExecutorName() {
        return executorName;
    }

    public String getTaskName() {
        return taskName;
    }

    public String getTaskId() {
        return taskId;
    }
}