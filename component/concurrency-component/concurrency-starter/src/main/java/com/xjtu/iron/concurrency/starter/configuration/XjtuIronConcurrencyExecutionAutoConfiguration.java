package com.xjtu.iron.concurrency.starter.configuration;

import com.xjtu.iron.concurrency.api.context.ContextAwareTaskDecorator;
import com.xjtu.iron.concurrency.api.error.AsyncErrorClassificationRule;
import com.xjtu.iron.concurrency.api.error.AsyncErrorClassifier;
import com.xjtu.iron.concurrency.api.execution.executor.AsyncExecutor;
import com.xjtu.iron.concurrency.api.execution.pool.ThreadPoolManager;
import com.xjtu.iron.concurrency.api.execution.pool.ThreadPoolSpec;
import com.xjtu.iron.concurrency.api.execution.registry.TaskExecutionRegistry;
import com.xjtu.iron.concurrency.api.execution.task.TaskCancellationManager;
import com.xjtu.iron.concurrency.api.execution.template.AsyncTemplate;
import com.xjtu.iron.concurrency.api.listener.AsyncUncaughtExceptionHandler;
import com.xjtu.iron.concurrency.api.listener.TaskExecutionListener;
import com.xjtu.iron.concurrency.config.ThreadPoolSpecResolver;
import com.xjtu.iron.concurrency.core.async.DefaultAsyncExecutor;
import com.xjtu.iron.concurrency.core.async.DefaultAsyncTemplate;
import com.xjtu.iron.concurrency.core.control.DefaultTaskCancellationManager;
import com.xjtu.iron.concurrency.core.control.DefaultTaskControlRegistry;
import com.xjtu.iron.concurrency.core.control.TaskControlRegistry;
import com.xjtu.iron.concurrency.core.error.CompositeAsyncErrorClassifier;
import com.xjtu.iron.concurrency.core.error.DefaultAsyncErrorClassifier;
import com.xjtu.iron.concurrency.core.execution.*;
import com.xjtu.iron.concurrency.core.lifecycle.DefaultTaskLifecyclePublisher;
import com.xjtu.iron.concurrency.core.lifecycle.TaskLifecyclePublisher;
import com.xjtu.iron.concurrency.core.listener.CompositeTaskExecutionListener;
import com.xjtu.iron.concurrency.core.listener.NoopAsyncUncaughtExceptionHandler;
import com.xjtu.iron.concurrency.core.metrics.ConcurrencyMetricsRecorder;
import com.xjtu.iron.concurrency.core.pipeline.DefaultTaskResultPipeline;
import com.xjtu.iron.concurrency.core.pipeline.TaskResultPipeline;
import com.xjtu.iron.concurrency.core.registry.DefaultTaskExecutionRegistry;
import com.xjtu.iron.concurrency.core.rejection.AwareAbortRejectedExecutionHandler;
import com.xjtu.iron.concurrency.core.rejection.CallerRunsRejectedExecutionHandler;
import com.xjtu.iron.concurrency.core.spi.*;
import com.xjtu.iron.concurrency.core.task.DefaultTaskExecutionTemplate;
import com.xjtu.iron.concurrency.core.task.RejectedTaskAware;
import com.xjtu.iron.concurrency.starter.properties.XjtuIronConcurrencyProperties;
import com.xjtu.iron.concurrency.starter.resolver.PropertiesThreadPoolSpecResolver;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 并发组件执行链路自动装配。
 *
 * <p>
 * 负责创建线程池注册表、错误分类规则链、生命周期发布器、结果处理管道、
 * 任务投递模板和业务入口 AsyncExecutor。
 * </p>
 */
@AutoConfiguration(after = XjtuIronConcurrencyContextAutoConfiguration.class)
public class XjtuIronConcurrencyExecutionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ThreadPoolSpecResolver threadPoolSpecResolver(
            XjtuIronConcurrencyProperties properties
    ) {
        return new PropertiesThreadPoolSpecResolver(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public RejectedExecutionHandlerFactory rejectedExecutionHandlerFactory() {
        return new DefaultRejectedExecutionHandlerFactory();
    }

    @Bean
    @ConditionalOnMissingBean
    public ThreadPoolFactory threadPoolFactory(
            RejectedExecutionHandlerFactory rejectedExecutionHandlerFactory
    ) {
        return new DefaultThreadPoolFactory(rejectedExecutionHandlerFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public ThreadPoolRegistry threadPoolRegistry(
            ThreadPoolSpecResolver threadPoolSpecResolver,
            ThreadPoolFactory threadPoolFactory,
            XjtuIronConcurrencyProperties properties
    ) {
        Map<String, ThreadPoolSpec> specs = threadPoolSpecResolver.resolveAll();

        if (!specs.containsKey(properties.getDefaultExecutor())) {
            throw new IllegalStateException("Default executor [" + properties.getDefaultExecutor() + "] not found");
        }

        DefaultThreadPoolRegistry registry = new DefaultThreadPoolRegistry();
        specs.forEach((poolName, spec) ->
                registry.register(poolName, threadPoolFactory.create(spec))
        );
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public ThreadPoolManager threadPoolManager(
            ThreadPoolRegistry threadPoolRegistry,
            RejectedExecutionHandlerFactory rejectedExecutionHandlerFactory
    ) {
        return new DefaultThreadPoolManager(
                threadPoolRegistry,
                rejectedExecutionHandlerFactory
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public TaskExecutionRegistry taskExecutionRegistry(
            XjtuIronConcurrencyProperties properties
    ) {
        return new DefaultTaskExecutionRegistry(
                properties.getTaskRegistry().getMaxSize()
        );
    }

    /**
     * 创建当前 JVM 运行任务控制注册表。
     */
    @Bean
    @ConditionalOnMissingBean
    public TaskControlRegistry taskControlRegistry() {
        return new DefaultTaskControlRegistry();
    }

    /**
     * 创建当前 JVM 任务取消管理器。
     */
    @Bean
    @ConditionalOnMissingBean
    public TaskCancellationManager taskCancellationManager(TaskControlRegistry taskControlRegistry) {
        return new DefaultTaskCancellationManager(taskControlRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public AsyncUncaughtExceptionHandler asyncUncaughtExceptionHandler() {
        return new NoopAsyncUncaughtExceptionHandler();
    }

    /**
     * 创建只处理 JDK 与并行组件通用异常的默认分类器。
     *
     * <p>
     * 该 Bean 作为 CompositeAsyncErrorClassifier 的最后兜底，不直接替代业务规则。
     * </p>
     */
    @Bean(name = "ironDefaultAsyncErrorClassifier")
    @ConditionalOnMissingBean(name = "ironDefaultAsyncErrorClassifier")
    public AsyncErrorClassifier ironDefaultAsyncErrorClassifier() {
        return new DefaultAsyncErrorClassifier();
    }

    /**
     * 创建最终注入任务主链路的组合错误分类器。
     *
     * <p>
     * 业务方可以注册任意数量的 AsyncErrorClassificationRule Bean；
     * Composite 会按 order 从小到大匹配，均不匹配时交给默认分类器。
     * </p>
     */
    @Bean(name = "ironAsyncErrorClassifier")
    @Primary
    @ConditionalOnMissingBean(name = "ironAsyncErrorClassifier")
    public AsyncErrorClassifier ironAsyncErrorClassifier(
            ObjectProvider<AsyncErrorClassificationRule> ruleProvider,
            @Qualifier("ironDefaultAsyncErrorClassifier")
            AsyncErrorClassifier fallbackClassifier
    ) {
        List<AsyncErrorClassificationRule> rules =
                ruleProvider.orderedStream().toList();

        return new CompositeAsyncErrorClassifier(
                rules,
                fallbackClassifier
        );
    }

    /**
     * 将业务注册的多个任务监听器组合成主链路只需依赖的单个监听器。
     */
    @Bean(name = "ironTaskExecutionListener")
    @ConditionalOnMissingBean(name = "ironTaskExecutionListener")
    public TaskExecutionListener ironTaskExecutionListener(ObjectProvider<TaskExecutionListener> listeners) {
        /*
         * 不在创建 AsyncExecutor 的阶段立即实例化全部业务监听器，
         * 避免 Listener Bean 间接依赖 AsyncExecutor 时形成循环依赖。
         */
        return new CompositeTaskExecutionListener(() ->
                listeners.orderedStream()
                        .filter(listener -> !(listener instanceof CompositeTaskExecutionListener))
                        .toList()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public TaskLifecyclePublisher taskLifecyclePublisher(
            ConcurrencyMetricsRecorder concurrencyMetricsRecorder,
            TaskExecutionRegistry taskExecutionRegistry,
            @Qualifier("ironTaskExecutionListener")
            TaskExecutionListener taskExecutionListener
    ) {
        return new DefaultTaskLifecyclePublisher(
                concurrencyMetricsRecorder,
                taskExecutionRegistry,
                taskExecutionListener
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public TaskResultPipeline taskResultPipeline(
            @Qualifier("ironAsyncErrorClassifier")
            AsyncErrorClassifier asyncErrorClassifier,
            TaskLifecyclePublisher taskLifecyclePublisher,
            @Qualifier("ironConcurrencyTimeoutScheduler")
            ScheduledExecutorService timeoutScheduler,
            @Qualifier("ironConcurrencyFallbackExecutor")
            Executor fallbackExecutor
    ) {
        return new DefaultTaskResultPipeline(
                asyncErrorClassifier,
                taskLifecyclePublisher,
                timeoutScheduler,
                fallbackExecutor
        );
    }




    @Bean(name = "ironConcurrencyTimeoutScheduler")
    @ConditionalOnMissingBean(name = "ironConcurrencyTimeoutScheduler")
    public ScheduledExecutorService ironConcurrencyTimeoutScheduler(
            XjtuIronConcurrencyProperties properties
    ) {
        XjtuIronConcurrencyProperties.PipelineProperties pipeline =
                properties.getPipeline();
        pipeline.validate();

        ScheduledThreadPoolExecutor scheduler =
                new ScheduledThreadPoolExecutor(
                        pipeline.getTimeoutSchedulerSize(),
                        new NamedThreadFactory(
                                pipeline.getTimeoutThreadNamePrefix(),
                                pipeline.isTimeoutDaemon()
                        ),
                        new ThreadPoolExecutor.AbortPolicy()
                );

        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        return scheduler;
    }

    /**
     * 创建 fallback 专用执行器。
     *
     * <p>
     * 仅允许 ABORT 或 CALLER_RUNS，避免 DISCARD 导致最终 Future 永远不完成，
     * 也避免 BLOCKING_WAIT 阻塞完成上游 Future 的线程。
     * </p>
     */
    @Bean(name = "ironConcurrencyFallbackExecutor")
    @ConditionalOnMissingBean(name = "ironConcurrencyFallbackExecutor")
    public ThreadPoolExecutor ironConcurrencyFallbackExecutor(
            XjtuIronConcurrencyProperties properties
    ) {
        XjtuIronConcurrencyProperties.PipelineProperties pipeline =
                properties.getPipeline();
        pipeline.validate();

        RejectedExecutionHandler rejectedExecutionHandler =
                pipeline.getFallbackRejectionPolicy()
                        == com.xjtu.iron.concurrency.api.enums.RejectionPolicy.CALLER_RUNS
                        ? new CallerRunsRejectedExecutionHandler()
                        : new AwareAbortRejectedExecutionHandler();

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                pipeline.getFallbackCorePoolSize(),
                pipeline.getFallbackMaxPoolSize(),
                pipeline.getFallbackKeepAliveTime().toMillis(),
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(pipeline.getFallbackQueueCapacity()),
                new NamedThreadFactory(
                        pipeline.getFallbackThreadNamePrefix(),
                        pipeline.isFallbackDaemon()
                ),
                rejectedExecutionHandler
        );

        executor.allowCoreThreadTimeOut(true);
        return executor;
    }


    @Bean
    @ConditionalOnMissingBean
    public TaskExecutionTemplate taskExecutionTemplate(
            ThreadPoolRegistry threadPoolRegistry,
            ContextAwareTaskDecorator contextAwareTaskDecorator,
            TaskLifecyclePublisher taskLifecyclePublisher,
            TaskResultPipeline taskResultPipeline,
            @Qualifier("ironAsyncErrorClassifier")
            AsyncErrorClassifier asyncErrorClassifier,
            AsyncUncaughtExceptionHandler asyncUncaughtExceptionHandler,
            TaskControlRegistry taskControlRegistry,
            TaskCancellationManager taskCancellationManager
    ) {
        return new DefaultTaskExecutionTemplate(
                threadPoolRegistry,
                contextAwareTaskDecorator,
                taskLifecyclePublisher,
                taskResultPipeline,
                asyncErrorClassifier,
                asyncUncaughtExceptionHandler,
                taskControlRegistry,
                taskCancellationManager
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public AsyncExecutor asyncExecutor(
            TaskExecutionTemplate taskExecutionTemplate,
            TaskCancellationManager taskCancellationManager
    ) {
        return new DefaultAsyncExecutor(
                taskExecutionTemplate,
                taskCancellationManager
        );
    }

    /**
     * 应用关闭时按照每个线程池配置执行优雅停机。
     */
    @Bean
    public DisposableBean threadPoolShutdownHook(
            ThreadPoolRegistry threadPoolRegistry,
            ThreadPoolSpecResolver threadPoolSpecResolver
    ) {
        return () -> {
            Map<String, ThreadPoolSpec> specs = threadPoolSpecResolver.resolveAll();

            for (Map.Entry<String, ThreadPoolExecutor> entry : threadPoolRegistry.snapshot().entrySet()) {
                String poolName = entry.getKey();
                ThreadPoolExecutor executor = entry.getValue();
                ThreadPoolSpec spec = specs.get(poolName);

                if (spec == null || !spec.isWaitForTasksToCompleteOnShutdown()) {
                    shutdownNowAndAbortPending(poolName, executor);
                    continue;
                }

                executor.shutdown();
                try {
                    if (!executor.awaitTermination(spec.getAwaitTermination().toMillis(), TimeUnit.MILLISECONDS)) {
                        shutdownNowAndAbortPending(poolName, executor);
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    shutdownNowAndAbortPending(poolName, executor);
                }
            }
        };
    }


    /**
     * 立即关闭线程池，并通知尚未开始执行的任务。
     *
     * <p>
     * ThreadPoolExecutor#shutdownNow() 会把队列中尚未开始执行的任务移除并返回。
     * 如果直接忽略这些任务，对应的 CompletableFuture 可能永远不会完成。
     * </p>
     *
     * @param poolName 线程池名称
     * @param executor 线程池
     */
    private void shutdownNowAndAbortPending(String poolName, ThreadPoolExecutor executor) {
        List<Runnable> pendingTasks = executor.shutdownNow();

        RuntimeException cause = new RuntimeException(
                "Executor shutdown before queued task started: " + poolName
        );

        for (Runnable runnable : pendingTasks) {
            if (runnable instanceof ShutdownAbortAware abortAware) {
                abortAware.abortOnShutdown(cause);
            } else if (runnable instanceof RejectedTaskAware rejectedTaskAware) {
                /*
                 * 兼容兜底：
                 * 如果不是 ShutdownAbortAware，但支持拒绝感知，至少让 Future 异常完成，
                 * 避免任务永久挂起。
                 */
                rejectedTaskAware.reject(cause);
            }
        }
    }

    /**
     * 应用关闭时优雅停止 fallback 执行器。
     *
     * <p>
     * fallback 队列中保存的是最终 Future 的恢复任务。如果直接 destroyMethod=shutdownNow，
     * Spring 会丢弃 shutdownNow() 返回的未执行任务，导致最终 Future 可能永远不完成。
     * 因此这里显式处理 pending Runnable，并通知 ShutdownAbortAware。
     * </p>
     */
    @Bean
    public DisposableBean fallbackExecutorShutdownHook(
            @Qualifier("ironConcurrencyFallbackExecutor") Executor fallbackExecutor,
            XjtuIronConcurrencyProperties properties
    ) {
        return () -> {
            if (!(fallbackExecutor instanceof ThreadPoolExecutor executor)) {
                return;
            }

            XjtuIronConcurrencyProperties.PipelineProperties pipeline = properties.getPipeline();
            pipeline.validate();

            executor.shutdown();
            try {
                if (!executor.awaitTermination(
                        pipeline.getFallbackAwaitTermination().toMillis(),
                        TimeUnit.MILLISECONDS
                )) {
                    shutdownNowAndAbortPendingFallback(executor);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                shutdownNowAndAbortPendingFallback(executor);
            }
        };
    }

    /**
     * 关闭 fallback 执行器，并通知尚未开始执行的 fallback 任务。
     */
    private void shutdownNowAndAbortPendingFallback(ThreadPoolExecutor executor) {
        List<Runnable> pendingTasks = executor.shutdownNow();
        RuntimeException cause = new RuntimeException(
                "Fallback executor shutdown before queued fallback task started"
        );

        for (Runnable runnable : pendingTasks) {
            if (runnable instanceof ShutdownAbortAware abortAware) {
                abortAware.abortOnShutdown(cause);
            }
        }
    }

    /**
     * 关闭结果层超时调度器。
     *
     * <p>
     * 超时调度器只负责触发 timeout 检查。关闭时丢弃未触发的 timeout 检查，
     * 不会像 fallback 一样导致最终 Future 必然挂起；原始任务仍可自行成功、失败或取消。
     * </p>
     */
    @Bean
    public DisposableBean timeoutSchedulerShutdownHook(
            @Qualifier("ironConcurrencyTimeoutScheduler") ScheduledExecutorService timeoutScheduler
    ) {
        return timeoutScheduler::shutdownNow;
    }

    @Bean
    @ConditionalOnMissingBean
    public AsyncTemplate asyncTemplate(@Qualifier("ironConcurrencyTimeoutScheduler") ScheduledExecutorService timeoutScheduler) {
        return new DefaultAsyncTemplate(timeoutScheduler);
    }
}
