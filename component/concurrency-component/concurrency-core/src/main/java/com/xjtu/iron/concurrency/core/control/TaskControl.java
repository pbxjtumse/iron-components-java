package com.xjtu.iron.concurrency.core.control;

import com.xjtu.iron.concurrency.api.execution.task.TaskCancelResult;
import com.xjtu.iron.concurrency.core.task.TaskCommand;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 单次任务运行控制对象。
 *
 * <p>
 * 同时持有线程池、TaskCommand 和最终 Future，使取消操作能够：
 * 先移除尚在队列中的命令，再更新 CANCELLED 生命周期，并取消最终 Future。
 * </p>
 *
 * @param <T> 任务结果类型
 */
public final class TaskControl<T> implements CancellableTask {

    /** 执行任务的线程池。 */
    private final ThreadPoolExecutor executor;

    /** 原始任务命令。 */
    private final TaskCommand<T> command;

    /** 最终返回给业务调用方的 Future。 */
    private final CompletableFuture<T> finalFuture;

    public TaskControl(
            ThreadPoolExecutor executor,
            TaskCommand<T> command,
            CompletableFuture<T> finalFuture
    ) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.command = Objects.requireNonNull(command, "command must not be null");
        this.finalFuture = Objects.requireNonNull(finalFuture, "finalFuture must not be null");
    }

    @Override
    public TaskCancelResult cancel(boolean mayInterruptIfRunning) {
        /*
         * 如果任务仍在工作队列中，先移除，避免后续被工作线程取出。
         * 如果已经运行，remove 返回 false，后续通过 interrupt 尽力中断。
         */
        executor.remove(command);

        boolean cancelled = command.completeCancelled(
                new CancellationException("Task cancelled by task control"),
                mayInterruptIfRunning
        );

        if (!cancelled) {
            return TaskCancelResult.ALREADY_COMPLETED;
        }

        /*
         * finalFuture 可能是 timeout/fallback 包装后的 Future，必须同步取消，
         * 才能让等待最终结果的调用方立即收到 CancellationException。
         */
        finalFuture.cancel(false);
        return TaskCancelResult.CANCELLED;
    }
}
