package com.xjtu.iron.concurrency.core.pipeline;

import com.xjtu.iron.concurrency.api.enums.error.AsyncErrorStage;
import com.xjtu.iron.concurrency.api.enums.task.AsyncTaskStatus;
import com.xjtu.iron.concurrency.api.error.AsyncError;
import com.xjtu.iron.concurrency.api.error.AsyncErrorClassifier;
import com.xjtu.iron.concurrency.api.error.CompletableFutureExceptionUtils;
import com.xjtu.iron.concurrency.api.event.TaskExecutionEvent;
import com.xjtu.iron.concurrency.api.exception.AsyncTaskException;
import com.xjtu.iron.concurrency.api.exception.ConcurrencyException;
import com.xjtu.iron.concurrency.core.lifecycle.TaskLifecyclePublisher;
import com.xjtu.iron.concurrency.core.spi.ShutdownAbortAware;
import com.xjtu.iron.concurrency.core.task.TaskCommand;
import com.xjtu.iron.concurrency.core.task.TaskExecutionContext;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * 默认任务结果处理管道。
 *
 * <p>
 * 该管道位于原始任务执行之后，负责为原始 {@link CompletableFuture} 增加
 * 结果层超时和 fallback 能力。原始任务的提交、运行、成功、失败、拒绝和取消
 * 仍由 {@link TaskCommand} 负责。
 * </p>
 *
 * <p>
 * 管道顺序固定为：先应用结果层超时，再应用 fallback。这样原始任务失败、
 * 线程池拒绝和结果层超时都可以统一进入 fallback。
 * </p>
 */
public final class DefaultTaskResultPipeline implements TaskResultPipeline {

    /**
     * 组合后的结构化错误分类器。
     *
     * <p>
     * 用于把 fallback 自身异常、fallback 执行器拒绝等异常转换成 {@link AsyncError}。
     * </p>
     */
    private final AsyncErrorClassifier errorClassifier;

    /**
     * 任务生命周期发布器。
     *
     * <p>
     * 用于统一更新指标、任务状态注册表和任务监听器。
     * </p>
     */
    private final TaskLifecyclePublisher lifecyclePublisher;

    /**
     * 结果层超时调度器。
     *
     * <p>
     * 只负责触发短小的超时检查，不执行 fallback、RPC 或其他耗时业务逻辑。
     * </p>
     */
    private final ScheduledExecutorService timeoutScheduler;

    /**
     * fallback 专用执行器。
     *
     * <p>
     * 避免 fallback 阻塞原始任务工作线程或超时调度线程。
     * </p>
     */
    private final Executor fallbackExecutor;

    /**
     * 创建默认任务结果处理管道。
     *
     * @param errorClassifier 错误分类器
     * @param lifecyclePublisher 生命周期发布器
     * @param timeoutScheduler 结果层超时调度器
     * @param fallbackExecutor fallback 专用执行器
     */
    public DefaultTaskResultPipeline(
            AsyncErrorClassifier errorClassifier,
            TaskLifecyclePublisher lifecyclePublisher,
            ScheduledExecutorService timeoutScheduler,
            Executor fallbackExecutor
    ) {
        this.errorClassifier = Objects.requireNonNull(
                errorClassifier,
                "errorClassifier must not be null"
        );
        this.lifecyclePublisher = Objects.requireNonNull(
                lifecyclePublisher,
                "lifecyclePublisher must not be null"
        );
        this.timeoutScheduler = Objects.requireNonNull(
                timeoutScheduler,
                "timeoutScheduler must not be null"
        );
        this.fallbackExecutor = Objects.requireNonNull(
                fallbackExecutor,
                "fallbackExecutor must not be null"
        );
    }

    /**
     * 在原始任务 Future 之上依次应用结果层超时和 fallback。
     *
     * @param context 单次任务执行上下文
     * @param command 原始任务命令
     * @param <T> 任务结果类型
     * @return 应用 timeout、fallback 后最终返回给调用方的 Future
     */
    @Override
    public <T> CompletableFuture<T> apply(TaskExecutionContext<T> context, TaskCommand<T> command) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(command, "command must not be null");

        CompletableFuture<T> result = context.getBaseFuture();

        if (context.getTask().getTimeout() != null) {
            result = applyTimeout(context, command, result);
        }

        if (context.hasFallback()) {
            result = applyFallback(context, result);
        }

        return result;
    }

    /**
     * 为原始结果增加非侵入式结果层超时。
     *
     * <p>
     * 该方法不会调用 {@code source.orTimeout(...)}，因为后者会直接修改原始 Future，
     * 可能让裸 {@link TimeoutException} 抢先完成原始结果，从而丢失组件生成的
     * {@link AsyncError} 和 {@link AsyncTaskException}。
     * </p>
     *
     * @param context 单次任务执行上下文
     * @param command 原始任务命令
     * @param source 原始结果 Future
     * @param <T> 任务结果类型
     * @return 带结果层超时能力的新 Future
     */
    private <T> CompletableFuture<T> applyTimeout(
            TaskExecutionContext<T> context,
            TaskCommand<T> command,
            CompletableFuture<T> source
    ) {
        Duration timeout = context.getTask().getTimeout();
        //创建一个新的Future
        CompletableFuture<T> result = new CompletableFuture<>();

        final ScheduledFuture<?> timeoutFuture;
        try {
            //1 此处存在并发情况 只有 timeout 真正抢到了原始任务结果，才能尝试中断底层线程。
            timeoutFuture = timeoutScheduler.schedule(
                    () -> handleTimeout(context, command, result, timeout),
                    timeout.toMillis(),
                    TimeUnit.MILLISECONDS
            );
        } catch (RejectedExecutionException schedulerRejected) {
            /*
             * 超时调度器通常只会在应用关闭阶段拒绝。此时不能把“调度器拒绝”伪造成原始任务失败或触发 fallback，否则原始任务稍后成功时会出现
             * 最终状态冲突。这里退化为直接返回 source，由原始任务结果决定最终状态。
             */
            return source;
        }
        //2 这里并没有发生执行，只是定义了执行期间的动作
        source.whenComplete((value, throwable) -> {
            boolean completed;

            if (throwable == null) {
                completed = result.complete(value);
            } else {
                Throwable unwrapped = CompletableFutureExceptionUtils.unwrap(throwable);
                completed = unwrapped instanceof CancellationException
                        ? result.cancel(false)
                        : result.completeExceptionally(unwrapped);
            }

            if (completed) {
                /*
                 * 原始任务先结束，取消尚未触发的超时任务。
                 * 调度器开启 removeOnCancelPolicy 后，会及时移除取消项。
                 */
                timeoutFuture.cancel(false);
            }
        });

        return result;
    }

    /**
     * 处理结果层超时竞争。
     *
     * @param context 单次任务执行上下文
     * @param command 原始任务命令
     * @param result 超时包装后的 Future
     * @param timeout 配置的超时时间
     * @param <T> 任务结果类型
     */
    private <T> void handleTimeout(TaskExecutionContext<T> context,
            TaskCommand<T> command,
            CompletableFuture<T> result,
            Duration timeout
    ) {
        if (result.isDone()) {
            return;
        }
        //设置超时时间
        TimeoutException timeoutException = new TimeoutException(
                "Async task result timeout after " + timeout.toMillis() + " ms"
        );

        /*
         * TaskCommand 内部通过 CAS 判断超时路径是否首次确定原始任务结果。
         * 只有 timeoutWon=true 才能发布 TIMEOUT 并尝试中断底层线程。
         */
        boolean timeoutWon = command.completeTimeout(
                timeoutException,
                AsyncErrorStage.WAIT_RESULT
        );
        //若是发生超时 需要中断原始任务
        if (timeoutWon && context.getTask().isCancelOnTimeout()) {
            command.interruptRunningIfNecessary(context.getTask().isInterruptOnTimeout());
        }
    }

    /**
     * 为异常结果增加 fallback。
     *
     * <p>
     * fallback 会在专用执行器中运行。原始结果正常完成时直接透传；原始结果异常完成时，
     * 先发布 FALLBACK，再异步执行 fallback，并最终发布 FALLBACK_SUCCESS 或
     * FALLBACK_FAILED。
     * </p>
     *
     * @param context 单次任务执行上下文
     * @param source 已经应用结果层超时的 Future
     * @param <T> 任务结果类型
     * @return 带 fallback 能力的新 Future
     */
    private <T> CompletableFuture<T> applyFallback(TaskExecutionContext<T> context, CompletableFuture<T> source) {
        CompletableFuture<T> result = new CompletableFuture<>();

        source.whenComplete((value, throwable) -> {
            if (throwable == null) {
                result.complete(value);
                return;
            }

            Throwable unwrapped = CompletableFutureExceptionUtils.unwrap(throwable);

            /*
             * 主动取消默认不触发 fallback。取消是调用方明确终止任务的控制语义，
             * 不应被降级值重新转换成“成功”。
             */
            if (source.isCancelled()
                    || unwrapped instanceof CancellationException
                    || context.getRuntime().getStatus() == AsyncTaskStatus.CANCELLED) {
                result.cancel(false);
                return;
            }

            AsyncError originalError = resolveOriginalError(context, throwable);

            context.getRuntime().markIntermediate(AsyncTaskStatus.FALLBACK);
            lifecyclePublisher.publish(context.event(AsyncTaskStatus.FALLBACK, originalError, "Fallback triggered"));
            try {
                fallbackExecutor.execute(new FallbackTask<>(context, throwable, originalError, result));
            } catch (RejectedExecutionException fallbackRejected) {
                completeFallbackFailure(context, fallbackRejected, result, "Fallback executor rejected task");
            }
        });

        return result;
    }


    /**
     * fallback 执行任务。
     *
     * <p>
     * 不能使用裸 lambda，因为 fallbackExecutor 在应用关闭时可能调用 shutdownNow()，
     * 线程池会把尚未执行的 Runnable 从队列中移出并返回。只有实现 ShutdownAbortAware，
     * 关闭钩子才能通知它完成最终 Future，避免调用方永久等待。
     * </p>
     */
    private final class FallbackTask<T> implements Runnable, ShutdownAbortAware {

        private final TaskExecutionContext<T> context;

        private final Throwable originalThrowable;

        private final AsyncError originalError;

        private final CompletableFuture<T> result;

        private FallbackTask(
                TaskExecutionContext<T> context,
                Throwable originalThrowable,
                AsyncError originalError,
                CompletableFuture<T> result
        ) {
            this.context = context;
            this.originalThrowable = originalThrowable;
            this.originalError = originalError;
            this.result = result;
        }

        @Override
        public void run() {
            executeFallback(context, originalThrowable, originalError, result);
        }

        @Override
        public void abortOnShutdown(Throwable cause) {
            completeFallbackFailure(context, cause, result, "Fallback aborted because fallback executor shutdown");
        }
    }

    /**
     * 在 fallback 专用执行器中执行降级逻辑。
     *
     * @param context 单次任务执行上下文
     * @param originalThrowable 原始任务异常
     * @param originalError 原始任务结构化错误
     * @param result 最终返回给调用方的 Future
     * @param <T> 任务结果类型
     */
    private <T> void executeFallback(
            TaskExecutionContext<T> context,
            Throwable originalThrowable,
            AsyncError originalError,
            CompletableFuture<T> result
    ) {
        Throwable fallbackInput = CompletableFutureExceptionUtils.rootCause(originalThrowable);

        if (fallbackInput == null) {
            fallbackInput = CompletableFutureExceptionUtils.unwrap(originalThrowable);
        }

        context.getRuntime().markFallbackRunning();

        try {
            T fallbackValue = context.getTask().getFallback().apply(fallbackInput);

            if (context.getRuntime().tryFinalize(AsyncTaskStatus.FALLBACK_SUCCESS)) {
                TaskExecutionEvent successEvent = context.event(AsyncTaskStatus.FALLBACK_SUCCESS, originalError,
                        "Fallback success"
                );

                lifecyclePublisher.publish(successEvent);
                lifecyclePublisher.publishCompleted(successEvent);
            }
            result.complete(fallbackValue);
        } catch (Throwable fallbackThrowable) {
            completeFallbackFailure(context, fallbackThrowable, result, "Fallback failed");
        } finally {
            context.getRuntime().clearFallbackThread();
        }
    }

    /**
     * 统一完成 fallback 失败结果。
     *
     * @param context 单次任务执行上下文
     * @param fallbackThrowable fallback 自身异常或 fallback 执行器拒绝异常
     * @param result 最终返回给调用方的 Future
     * @param message 事件说明
     * @param <T> 任务结果类型
     */
    private <T> void completeFallbackFailure(
            TaskExecutionContext<T> context,
            Throwable fallbackThrowable,
            CompletableFuture<T> result,
            String message
    ) {
        if (context.getRuntime().getStatus() == AsyncTaskStatus.CANCELLED
                || result.isCancelled()) {
            result.cancel(false);
            return;
        }

        AsyncError fallbackError = errorClassifier.classify(
                context.getMetadata(),
                fallbackThrowable,
                AsyncErrorStage.FALLBACK
        );

        AsyncTaskException fallbackException = new AsyncTaskException(
                context.getTask().getExecutorName(),
                context.getTask().getTaskName(),
                context.getTask().getTaskId(),
                fallbackError,
                fallbackThrowable
        );

        if (context.getRuntime().tryFinalize(
                AsyncTaskStatus.FALLBACK_FAILED
        )) {
            TaskExecutionEvent failureEvent = context.event(
                    AsyncTaskStatus.FALLBACK_FAILED,
                    fallbackError,
                    message
            );

            lifecyclePublisher.publish(failureEvent);
            lifecyclePublisher.publishCompleted(failureEvent);
        }

        result.completeExceptionally(fallbackException);
    }

    /**
     * 获取原始任务已经生成的结构化错误。
     *
     * <p>
     * TaskCommand 生成的 {@link ConcurrencyException} 已经携带业务错误码、场景码和
     * 恢复建议，fallback 阶段应优先复用，避免重新分类后丢失业务信息。
     * </p>
     *
     * @param context 单次任务执行上下文
     * @param throwable 原始异常
     * @return 原始任务结构化错误
     */
    private AsyncError resolveOriginalError(TaskExecutionContext<?> context, Throwable throwable) {
        Throwable unwrapped = CompletableFutureExceptionUtils.unwrap(throwable);

        if (unwrapped instanceof ConcurrencyException concurrencyException
                && concurrencyException.getError() != null) {
            return concurrencyException.getError().copy();
        }

        return errorClassifier.classify(context.getMetadata(), throwable, originalErrorStage(context));
    }

    /**
     * 根据原始任务状态推断未包装异常的发生阶段。
     *
     * @param context 单次任务执行上下文
     * @return 推断出的错误阶段
     */
    private AsyncErrorStage originalErrorStage(
            TaskExecutionContext<?> context
    ) {
        AsyncTaskStatus status = context.getRuntime().getStatus();

        if (status == null) {
            return AsyncErrorStage.NONE;
        }

        return switch (status) {
            case REJECTED -> AsyncErrorStage.SUBMIT;
            case TIMEOUT -> AsyncErrorStage.WAIT_RESULT;
            case CANCELLED -> AsyncErrorStage.CANCEL;
            case FAILED -> AsyncErrorStage.RUN;
            case FALLBACK, FALLBACK_SUCCESS, FALLBACK_FAILED -> AsyncErrorStage.FALLBACK;
            case CREATED, SUBMITTED, RUNNING, SUCCESS -> AsyncErrorStage.NONE;
        };
    }
}
