package com.xjtu.iron.concurrency.core.task;

import com.xjtu.iron.concurrency.api.execution.task.AsyncTask;
import com.xjtu.iron.concurrency.api.retry.RetryPolicy;
import com.xjtu.iron.concurrency.api.task.TaskMetadata;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 单次任务执行定义快照。
 *
 * <p>
 * AsyncTask 是用户提交时使用的可变任务定义；
 * TaskDefinition 是任务提交之后在 core 内部使用的不可变快照。
 * TaskDefinition 能冻结 AsyncTask 上的字段选择 但不能冻结业务 lambda 内部捕获的外部可变对象
 * </p>
 *
 * @param <T> 任务返回值类型
 */
public final class TaskDefinition<T> {

    /**
     * 固定任务元数据。
     */
    private final TaskMetadata metadata;

    /**
     * 用户原始执行逻辑。
     */
    private final Supplier<T> operation;

    /**
     * 结果层超时时间。
     */
    private final Duration timeout;

    /**
     * 排队超时时间。
     */
    private final Duration queueTimeout;

    /**
     * 结果层超时后是否尝试取消底层任务。
     */
    private final boolean cancelOnTimeout;

    /**
     * 结果超时触发取消时是否尝试中断运行线程。
     */
    private final boolean interruptOnTimeout;

    /**
     * 原始任务失败、拒绝或超时后的 fallback 逻辑。
     */
    private final Function<Throwable, T> fallback;

    /**
     * 是否启用上下文传播。
     */
    private final boolean contextPropagation;

    /**
     * 重试策略快照。
     *
     * <p>
     * 当前 RetryPolicy 仍然是可变对象，后续建议增加 RetryPolicy.copy()。一期先保存引用，但约定提交后不再修改 RetryPolicy。
     * </p>
     */
    private final RetryPolicy retryPolicy;

    public TaskDefinition(
            TaskMetadata metadata,
            Supplier<T> operation,
            Duration timeout,
            Duration queueTimeout,
            boolean cancelOnTimeout,
            boolean interruptOnTimeout,
            Function<Throwable, T> fallback,
            boolean contextPropagation,
            RetryPolicy retryPolicy
    ) {
        this.metadata = Objects.requireNonNull(metadata, "metadata must not be null");
        this.operation = Objects.requireNonNull(operation, "operation must not be null");
        this.timeout = timeout;
        this.queueTimeout = queueTimeout;
        this.cancelOnTimeout = cancelOnTimeout;
        this.interruptOnTimeout = interruptOnTimeout;
        this.fallback = fallback;
        this.contextPropagation = contextPropagation;
        this.retryPolicy = retryPolicy == null ? RetryPolicy.none() : retryPolicy;
    }

    /**
     * 从用户提交的 AsyncTask 创建不可变执行定义快照。
     *
     * <p>
     * 调用前要求 AsyncTask 已经完成 validate()。
     * </p>
     *
     * @param task 用户任务定义
     * @param <T> 任务返回值类型
     * @return 不可变任务定义快照
     */
    public static <T> TaskDefinition<T> from(AsyncTask<T> task) {
        Objects.requireNonNull(task, "task must not be null");

        return new TaskDefinition<>(
                task.metadata(),
                task.getOperation(),
                task.getTimeout(),
                task.getQueueTimeout(),
                task.isCancelOnTimeout(),
                task.isInterruptOnTimeout(),
                task.getFallback(),
                task.isContextPropagation(),
                task.getRetryPolicy()
        );
    }

    public TaskMetadata getMetadata() {
        return metadata;
    }

    public String getTaskId() {
        return metadata.getTaskId();
    }

    public String getExecutorName() {
        return metadata.getExecutorName();
    }

    public String getTaskName() {
        return metadata.getTaskName();
    }

    public String getBizKey() {
        return metadata.getBizKey();
    }

    public Supplier<T> getOperation() {
        return operation;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public Duration getQueueTimeout() {
        return queueTimeout;
    }

    public boolean isCancelOnTimeout() {
        return cancelOnTimeout;
    }

    public boolean isInterruptOnTimeout() {
        return interruptOnTimeout;
    }

    /**
     * 兼容旧命名：该字段实际表示结果超时触发取消时是否中断运行线程。
     */
    @Deprecated
    public boolean isInterruptOnCancel() {
        return interruptOnTimeout;
    }

    public Function<Throwable, T> getFallback() {
        return fallback;
    }

    public boolean isContextPropagation() {
        return contextPropagation;
    }

    public RetryPolicy getRetryPolicy() {
        return retryPolicy;
    }
}
