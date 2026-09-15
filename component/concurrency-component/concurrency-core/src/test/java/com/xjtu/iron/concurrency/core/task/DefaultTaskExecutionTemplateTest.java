package com.xjtu.iron.concurrency.core.task;

import com.xjtu.iron.concurrency.api.enums.RejectionPolicy;
import com.xjtu.iron.concurrency.api.enums.task.AsyncTaskStatus;
import com.xjtu.iron.concurrency.api.event.TaskExecutionEvent;
import com.xjtu.iron.concurrency.api.execution.pool.ThreadPoolSpec;
import com.xjtu.iron.concurrency.api.execution.task.AsyncTask;
import com.xjtu.iron.concurrency.api.execution.task.TaskCancelResult;
import com.xjtu.iron.concurrency.api.execution.task.TaskHandle;
import com.xjtu.iron.concurrency.core.context.DefaultContextAwareTaskDecorator;
import com.xjtu.iron.concurrency.core.context.NoopContextPropagator;
import com.xjtu.iron.concurrency.core.control.DefaultTaskCancellationManager;
import com.xjtu.iron.concurrency.core.control.DefaultTaskControlRegistry;
import com.xjtu.iron.concurrency.core.control.TaskControlRegistry;
import com.xjtu.iron.concurrency.core.error.DefaultAsyncErrorClassifier;
import com.xjtu.iron.concurrency.core.execution.DefaultRejectedExecutionHandlerFactory;
import com.xjtu.iron.concurrency.core.execution.DefaultThreadPoolFactory;
import com.xjtu.iron.concurrency.core.execution.DefaultThreadPoolRegistry;
import com.xjtu.iron.concurrency.core.lifecycle.TaskLifecyclePublisher;
import com.xjtu.iron.concurrency.core.pipeline.DefaultTaskResultPipeline;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class DefaultTaskExecutionTemplateTest {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ThreadPoolExecutor fallbackExecutor = new ThreadPoolExecutor(
            1,
            1,
            60L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(10)
    );
    private final DefaultThreadPoolRegistry registry = new DefaultThreadPoolRegistry();
    private final TaskControlRegistry controlRegistry = new DefaultTaskControlRegistry();
    private final RecordingPublisher publisher = new RecordingPublisher();
    private final DefaultAsyncErrorClassifier classifier = new DefaultAsyncErrorClassifier();

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
        fallbackExecutor.shutdownNow();
        registry.snapshot().values().forEach(ThreadPoolExecutor::shutdownNow);
    }

    @Test
    void submitSuccessPublishesExpectedEvents() throws Exception {
        registerPool("default", 1, 1, RejectionPolicy.ABORT);
        DefaultTaskExecutionTemplate template = newTemplate();

        CompletableFuture<String> future = template.submit(AsyncTask.of("default", "success", () -> "ok"));

        assertEquals("ok", future.get(1, TimeUnit.SECONDS));
        assertTrue(publisher.events.contains(AsyncTaskStatus.SUBMITTED));
        assertTrue(publisher.events.contains(AsyncTaskStatus.RUNNING));
        assertTrue(publisher.events.contains(AsyncTaskStatus.SUCCESS));
    }

    @Test
    void submitFailureCanFallback() throws Exception {
        registerPool("default", 1, 1, RejectionPolicy.ABORT);
        DefaultTaskExecutionTemplate template = newTemplate();

        CompletableFuture<String> future = template.submit(
                AsyncTask.<String>of("default", "fallback", () -> { throw new IllegalStateException("bad"); })
                        .fallback(error -> "fallback")
        );

        assertEquals("fallback", future.get(1, TimeUnit.SECONDS));
        assertTrue(publisher.events.contains(AsyncTaskStatus.FAILED));
        assertTrue(publisher.events.contains(AsyncTaskStatus.FALLBACK));
        assertTrue(publisher.events.contains(AsyncTaskStatus.FALLBACK_SUCCESS));
    }

    @Test
    void tryExecuteReturnsFalseWhenDiscardRejects() {
        ThreadPoolExecutor executor = registerPool("discard", 1, 1, RejectionPolicy.DISCARD);
        CountDownLatch release = new CountDownLatch(1);
        executor.execute(() -> await(release));
        assertTrue(executor.getQueue().offer(() -> { }));
        DefaultTaskExecutionTemplate template = newTemplate();

        boolean accepted = template.tryExecute("discard", "discard-task", () -> { });

        assertFalse(accepted);
        release.countDown();
    }

    @Test
    void cancelQueuedTaskCompletesHandleAsCancelled() throws Exception {
        ThreadPoolExecutor executor = registerPool("default", 1, 1, RejectionPolicy.ABORT);
        CountDownLatch release = new CountDownLatch(1);
        executor.execute(() -> await(release));
        DefaultTaskExecutionTemplate template = newTemplate();

        TaskHandle<String> handle = template.submitHandle(AsyncTask.of("default", "queued", () -> "never"));
        TaskCancelResult result = handle.cancel(false);

        assertEquals(TaskCancelResult.CANCELLED, result);
        assertTrue(handle.getFuture().isCancelled());
        assertTrue(publisher.events.contains(AsyncTaskStatus.CANCELLED));
        release.countDown();
    }

    private DefaultTaskExecutionTemplate newTemplate() {
        DefaultTaskCancellationManager cancellationManager = new DefaultTaskCancellationManager(controlRegistry);
        return new DefaultTaskExecutionTemplate(
                registry,
                new DefaultContextAwareTaskDecorator(new NoopContextPropagator()),
                publisher,
                new DefaultTaskResultPipeline(classifier, publisher, scheduler, fallbackExecutor),
                classifier,
                (event, throwable) -> { },
                controlRegistry,
                cancellationManager
        );
    }

    private ThreadPoolExecutor registerPool(String name, int core, int queueCapacity, RejectionPolicy policy) {
        ThreadPoolSpec spec = new ThreadPoolSpec();
        spec.setName(name);
        spec.setCorePoolSize(core);
        spec.setMaxPoolSize(core);
        spec.setQueueCapacity(queueCapacity);
        spec.setRejectionPolicy(policy);
        spec.setKeepAliveTime(Duration.ofMillis(100));
        spec.setAwaitTermination(Duration.ofMillis(100));
        spec.validate();
        ThreadPoolExecutor executor = new DefaultThreadPoolFactory(new DefaultRejectedExecutionHandlerFactory()).create(spec);
        registry.register(name, executor);
        return executor;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class RecordingPublisher implements TaskLifecyclePublisher {
        private final List<AsyncTaskStatus> events = new CopyOnWriteArrayList<>();

        @Override
        public void publish(TaskExecutionEvent event) {
            events.add(event.getStatus());
        }

        @Override
        public void publishCompleted(TaskExecutionEvent terminalEvent) {
            events.add(terminalEvent.getStatus());
        }
    }
}
