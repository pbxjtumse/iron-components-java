package com.xjtu.iron.concurrency.api.error;

import com.xjtu.iron.concurrency.api.enums.error.AsyncErrorStage;
import com.xjtu.iron.concurrency.api.task.TaskMetadata;

import java.util.Objects;

/**
 * 异步错误分类上下文。
 *
 * <p>
 * 分类规则只依赖任务元数据、异常和异常阶段，不再依赖可变的 AsyncTask。
 * 这样任务提交后即使外部继续修改 AsyncTask，也不会影响错误分类结果。
 * </p>
 */
public final class AsyncErrorClassificationContext {

    /**
     * 当前任务元数据快照。
     */
    private final TaskMetadata task;

    /**
     * 尚未剥离 CompletionException、ExecutionException 等包装的原始异常。
     */
    private final Throwable throwable;

    /**
     * 只剥离 CompletionException、ExecutionException 后的异常。
     */
    private final Throwable unwrapped;

    /**
     * 已经剥离包装并遍历 cause 链后得到的根因异常。
     */
    private final Throwable rootCause;

    /**
     * 异常发生阶段。
     */
    private final AsyncErrorStage stage;

    public AsyncErrorClassificationContext(
            TaskMetadata task,
            Throwable throwable,
            Throwable unwrapped,
            Throwable rootCause,
            AsyncErrorStage stage
    ) {
        this.task = Objects.requireNonNull(task, "task metadata must not be null");
        this.throwable = throwable;
        this.unwrapped = unwrapped;
        this.rootCause = rootCause;
        this.stage = stage == null ? AsyncErrorStage.NONE : stage;
    }

    /**
     * 从任务元数据、异常和阶段构造分类上下文。
     */
    public static AsyncErrorClassificationContext of(
            TaskMetadata task,
            Throwable throwable,
            AsyncErrorStage stage
    ) {
        Throwable unwrapped = CompletableFutureExceptionUtils.unwrap(throwable);
        Throwable rootCause = CompletableFutureExceptionUtils.rootCause(throwable);
        return new AsyncErrorClassificationContext(
                task,
                throwable,
                unwrapped,
                rootCause,
                stage
        );
    }

    /**
     * 获取任务元数据。
     *
     * <p>
     * 方法名保留为 getTask，方便已有业务规则从 context.getTask().getTaskId()
     * 平滑迁移；实际返回的是不可变 TaskMetadata，而不是可变 AsyncTask。
     * </p>
     */
    public TaskMetadata getTask() {
        return task;
    }

    public TaskMetadata getMetadata() {
        return task;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public Throwable getUnwrapped() {
        return unwrapped;
    }

    public Throwable getRootCause() {
        return rootCause;
    }

    public AsyncErrorStage getStage() {
        return stage;
    }
}
