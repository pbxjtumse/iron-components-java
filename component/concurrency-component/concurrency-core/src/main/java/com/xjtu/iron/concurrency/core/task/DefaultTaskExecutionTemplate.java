package com.xjtu.iron.concurrency.core.task;

import com.xjtu.iron.concurrency.api.context.ContextAwareTaskDecorator;
import com.xjtu.iron.concurrency.api.enums.error.AsyncErrorStage;
import com.xjtu.iron.concurrency.api.error.AsyncError;
import com.xjtu.iron.concurrency.api.error.AsyncErrorClassifier;
import com.xjtu.iron.concurrency.api.exception.ConcurrencyRejectedException;
import com.xjtu.iron.concurrency.api.execution.task.AsyncTask;
import com.xjtu.iron.concurrency.api.execution.task.TaskCancellationManager;
import com.xjtu.iron.concurrency.api.execution.task.TaskHandle;
import com.xjtu.iron.concurrency.api.listener.AsyncUncaughtExceptionHandler;
import com.xjtu.iron.concurrency.api.task.TaskResultMode;
import com.xjtu.iron.concurrency.core.control.DefaultTaskHandle;
import com.xjtu.iron.concurrency.core.control.TaskControl;
import com.xjtu.iron.concurrency.core.control.TaskControlRegistry;
import com.xjtu.iron.concurrency.core.lifecycle.TaskLifecyclePublisher;
import com.xjtu.iron.concurrency.core.pipeline.TaskResultPipeline;
import com.xjtu.iron.concurrency.core.spi.TaskExecutionTemplate;
import com.xjtu.iron.concurrency.core.spi.ThreadPoolRegistry;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Supplier;

/**
 * 默认任务投递模板。
 *
 * <p>
 * 该类负责把用户可变 AsyncTask 固化为 TaskDefinition，创建上下文、发布 SUBMITTED、
 * 装配 timeout/fallback 管道，并把 TaskCommand 提交到业务线程池。
 * </p>
 */
public final class DefaultTaskExecutionTemplate implements TaskExecutionTemplate {

    /**
     * 线程池注册表。
     */
    private final ThreadPoolRegistry threadPoolRegistry;

    /**
     * 上下文任务装饰器。
     */
    private final ContextAwareTaskDecorator taskDecorator;

    /**
     * 任务生命周期发布器。
     */
    private final TaskLifecyclePublisher lifecyclePublisher;

    /**
     * timeout 与 fallback 结果管道。
     */
    private final TaskResultPipeline resultPipeline;

    /**
     * 组合后的错误分类器。
     */
    private final AsyncErrorClassifier errorClassifier;

    /** fire-and-forget 异常处理器。 */
    private final AsyncUncaughtExceptionHandler uncaughtExceptionHandler;

    /** 当前节点运行任务控制注册表。 */
    private final TaskControlRegistry taskControlRegistry;

    /** 当前节点任务取消管理器。 */
    private final TaskCancellationManager cancellationManager;

    public DefaultTaskExecutionTemplate(
            ThreadPoolRegistry threadPoolRegistry,
            ContextAwareTaskDecorator taskDecorator,
            TaskLifecyclePublisher lifecyclePublisher,
            TaskResultPipeline resultPipeline,
            AsyncErrorClassifier errorClassifier,
            AsyncUncaughtExceptionHandler uncaughtExceptionHandler,
            TaskControlRegistry taskControlRegistry,
            TaskCancellationManager cancellationManager
    ) {
        this.threadPoolRegistry = Objects.requireNonNull(threadPoolRegistry, "threadPoolRegistry must not be null");
        this.taskDecorator = Objects.requireNonNull(taskDecorator, "taskDecorator must not be null");
        this.lifecyclePublisher = Objects.requireNonNull(lifecyclePublisher, "lifecyclePublisher must not be null");
        this.resultPipeline = Objects.requireNonNull(resultPipeline, "resultPipeline must not be null");
        this.errorClassifier = Objects.requireNonNull(errorClassifier, "errorClassifier must not be null");
        this.uncaughtExceptionHandler = Objects.requireNonNull(uncaughtExceptionHandler, "uncaughtExceptionHandler must not be null");
        this.taskControlRegistry = Objects.requireNonNull(taskControlRegistry, "taskControlRegistry must not be null");
        this.cancellationManager = Objects.requireNonNull(cancellationManager, "cancellationManager must not be null");
    }

    @Override
    public void execute(String executorName, String taskName, Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable must not be null");
        AsyncTask<Void> task = AsyncTask.of(executorName, taskName, () -> {
            runnable.run();
            return null;
        });
        submitInternal(task, TaskResultMode.FIRE_AND_FORGET);
    }

    @Override
    public boolean tryExecute(String executorName, String taskName, Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable must not be null");
        AsyncTask<Void> task = AsyncTask.of(executorName, taskName, () -> {
            runnable.run();
            return null;
        });

        try {
            Submission<Void> submission = submitInternal(task, TaskResultMode.FIRE_AND_FORGET);
            return !submission.command().isRejected();
        } catch (ConcurrencyRejectedException rejected) {
            return false;
        }
    }

    @Override
    public CompletableFuture<Void> run(String executorName, String taskName, Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable must not be null");
        return submit(AsyncTask.of(executorName, taskName, () -> {
            runnable.run();
            return null;
        }));
    }

    @Override
    public <T> CompletableFuture<T> supply(String executorName, String taskName, Supplier<T> supplier) {
        return submit(AsyncTask.of(executorName, taskName, supplier));
    }

    @Override
    public <T> CompletableFuture<T> submit(AsyncTask<T> task) {
        return submitHandle(task).getFuture();
    }

    @Override
    public <T> TaskHandle<T> submitHandle(AsyncTask<T> task) {
        return submitInternal(task, TaskResultMode.RESULT_AWARE).handle();
    }

    /**
     * 执行统一任务提交流程。
     * @param task 业务传入的任务定义
     * @param resultMode 当前任务是否关心最终结果
     *                    RESULT_AWARE 业务关心最终 Future
     *                    FIRE_AND_FORGET 业务不关心最终结果。
     * @return 结果 T
     */
    private <T> Submission<T> submitInternal(AsyncTask<T> task, TaskResultMode resultMode) {
        Objects.requireNonNull(task, "task must not be null");
        task.validate();
        //1. 创建不可变快照
        TaskDefinition<T> definition = TaskDefinition.from(task);
        //2.获取具体执行executor
        ThreadPoolExecutor executor = threadPoolRegistry.getExecutor(definition.getExecutorName());
        //3.创建 executable 决定是否做上下文传播
        CompletableFuture<T> baseFuture = new CompletableFuture<>();
        Supplier<T> executable = getExecutable(definition);
        //4. 创建本次任务的运行时状态机
        TaskExecutionRuntime runtime = new TaskExecutionRuntime(resultMode);
        //5.本次执行需要的东西装起来，后续 TaskCommand、TaskResultPipeline 都通过 context 获取
        TaskExecutionContext<T> context = new TaskExecutionContext<>(definition, executable, baseFuture, runtime);
        //6.创建 TaskCommand 是真正提交给线程池的 Runnable
        TaskCommand<T> command = new TaskCommand<>(context, lifecyclePublisher, errorClassifier, uncaughtExceptionHandler);
        //7.设置提交状态
        command.submitted();
        //6. 执行增强 time fallback 逻辑
        CompletableFuture<T> finalFuture = resultMode == TaskResultMode.RESULT_AWARE
                ? resultPipeline.apply(context, command)
                : baseFuture;
        //7.初始化
        TaskControl<T> control = new TaskControl<>(executor, command, finalFuture);
        TaskHandle<T> handle = new DefaultTaskHandle<>(definition.getTaskId(), finalFuture, cancellationManager);
        //9 注册 TaskControl 这一步让外部可以通过 taskId 找到任务控制对象
        taskControlRegistry.register(definition.getTaskId(), control);
        finalFuture.whenComplete((value, throwable) ->
                taskControlRegistry.remove(definition.getTaskId(), control));
        try {
            /*
             * executor.execute 内部可能触发 ThreadPoolExecutor 的拒绝流程。
             * 如果线程池无法接收任务，会回调当前配置的 RejectedExecutionHandler.rejectedExecution。
             *
             * 对增强版拒绝策略：
             * - 如果策略成功补救，例如阻塞等待后重新入队，则 execute 正常返回；
             * - 如果策略无法补救，会通过 RejectedTaskSupport 标记 TaskCommand 为 REJECTED，并抛出 RejectedExecutionException。
             */
            executor.execute(command);
        } catch (RejectedExecutionException rejected) {
            /*
             * 增强版拒绝策略通常已经通过 RejectedTaskSupport 通知了 TaskCommand。
             * 如果使用的是未感知 TaskCommand 的原生/JDK/第三方拒绝策略，
             * 这里作为最后兜底补充一次拒绝通知。
             */
            if (!command.isBaseOutcomeResolved()) {
                command.reject(rejected);
            }
            if (resultMode == TaskResultMode.FIRE_AND_FORGET) {
                AsyncError error = errorClassifier.classify(definition.getMetadata(), rejected, AsyncErrorStage.SUBMIT);
                throw new ConcurrencyRejectedException(
                        definition.getExecutorName(),
                        definition.getTaskName(),
                        error,
                        rejected
                );
            }
        }

        return new Submission<>(handle, command);
    }

    private <T> Supplier<T> getExecutable(TaskDefinition<T> definition) {
        Supplier<T> executable = definition.isContextPropagation()
                ? taskDecorator.decorate(definition.getOperation())
                : definition.getOperation();
        return executable;
    }

    /**
     * 内部提交结果，同时保留句柄和命令，供 tryExecute 判断同步拒绝。
     */
    private record Submission<T>(TaskHandle<T> handle, TaskCommand<T> command) {
    }
}
