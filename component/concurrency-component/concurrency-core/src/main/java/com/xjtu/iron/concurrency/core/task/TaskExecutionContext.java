package com.xjtu.iron.concurrency.core.task;

import com.xjtu.iron.concurrency.api.enums.task.AsyncTaskStatus;
import com.xjtu.iron.concurrency.api.error.AsyncError;
import com.xjtu.iron.concurrency.api.event.TaskExecutionEvent;
import com.xjtu.iron.concurrency.api.task.TaskMetadata;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * 单次任务执行上下文。
 *
 * <p>
 * 保存本次执行实例使用的不可变任务定义、装饰后逻辑、原始结果 Future 和动态运行时状态。
 * TaskCommand、TaskResultPipeline、错误分类器都从这里读取同一份任务快照。
 * </p>
 *
 * @param <T> 任务返回值类型
 */
public final class TaskExecutionContext<T> {

    /**
     * 本次任务执行定义快照。
     */
    private final TaskDefinition<T> task;

    /**
     * 本次执行固定的只读任务元数据。
     */
    private final TaskMetadata metadata;

    /**
     * 真正在线程池中执行的逻辑。
     */
    private final Supplier<T> executable;

    /**
     * 原始任务结果 Future。
     */
    private final CompletableFuture<T> baseFuture;

    /**
     * 本次执行的动态运行时状态。
     */
    private final TaskExecutionRuntime runtime;

    public TaskExecutionContext(
            TaskDefinition<T> task,
            Supplier<T> executable,
            CompletableFuture<T> baseFuture,
            TaskExecutionRuntime runtime
    ) {
        this.task = Objects.requireNonNull(task, "task must not be null");
        this.metadata = Objects.requireNonNull(task.getMetadata(), "task metadata must not be null");
        this.executable = Objects.requireNonNull(executable, "executable must not be null");
        this.baseFuture = Objects.requireNonNull(baseFuture, "baseFuture must not be null");
        this.runtime = Objects.requireNonNull(runtime, "runtime must not be null");
    }

    /**
     * 根据当前运行时状态创建生命周期事件。
     */
    public TaskExecutionEvent event(AsyncTaskStatus status, AsyncError error, String message) {
        return new TaskExecutionEvent(
                metadata,
                status,
                runtime.getResultMode(),
                runtime.getExecutionMode(),
                runtime.timingSnapshot(),
                error,
                message,
                Instant.now()
        );
    }

    /**
     * 判断当前任务是否配置 fallback。
     */
    public boolean hasFallback() {
        return task.getFallback() != null;
    }

    public TaskDefinition<T> getTask() {
        return task;
    }

    public TaskMetadata getMetadata() {
        return metadata;
    }

    public Supplier<T> getExecutable() {
        return executable;
    }

    public CompletableFuture<T> getBaseFuture() {
        return baseFuture;
    }

    public TaskExecutionRuntime getRuntime() {
        return runtime;
    }
}
