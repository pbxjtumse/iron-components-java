package com.xjtu.iron.concurrency.api.exception;


import com.xjtu.iron.concurrency.api.error.AsyncError;

/**
 * 并行任务拒绝异常。
 *
 * <p>
 * 当线程池队列满、线程数达到上限，或者拒绝策略主动拒绝任务时，并行组件会使用该异常表示任务未能成功提交执行。
 * </p>
 */
public class ConcurrencyRejectedException extends ConcurrencyException {

    /**
     * 线程池名称。
     */
    private final String executorName;

    /**
     * 任务名称。
     */
    private final String taskName;

    public ConcurrencyRejectedException(String executorName, String taskName, AsyncError error, Throwable cause) {
        super("Async task rejected: executor=" + executorName + ", task=" + taskName,
                error,
                cause);
        this.executorName = executorName;
        this.taskName = taskName;
    }

    public String getExecutorName() {
        return executorName;
    }

    public String getTaskName() {
        return taskName;
    }
}