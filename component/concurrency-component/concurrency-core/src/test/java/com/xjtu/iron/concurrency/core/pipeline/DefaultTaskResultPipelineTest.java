package com.xjtu.iron.concurrency.core.pipeline;

import com.xjtu.iron.concurrency.api.enums.task.AsyncTaskStatus;
import com.xjtu.iron.concurrency.api.error.AsyncErrorClassifier;
import com.xjtu.iron.concurrency.api.event.TaskExecutionEvent;
import com.xjtu.iron.concurrency.api.exception.AsyncTaskException;
import com.xjtu.iron.concurrency.api.execution.task.AsyncTask;
import com.xjtu.iron.concurrency.api.task.TaskResultMode;
import com.xjtu.iron.concurrency.core.error.DefaultAsyncErrorClassifier;
import com.xjtu.iron.concurrency.core.lifecycle.TaskLifecyclePublisher;
import com.xjtu.iron.concurrency.core.spi.ShutdownAbortAware;
import com.xjtu.iron.concurrency.core.task.TaskCommand;
import com.xjtu.iron.concurrency.core.task.TaskDefinition;
import com.xjtu.iron.concurrency.core.task.TaskExecutionContext;
import com.xjtu.iron.concurrency.core.task.TaskExecutionRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class DefaultTaskResultPipelineTest {

    private final ScheduledExecutorService timeoutScheduler = Executors.newSingleThreadScheduledExecutor();

    private ThreadPoolExecutor fallbackExecutor;

    @AfterEach
    void tearDown() {
        timeoutScheduler.shutdownNow();
        if (fallbackExecutor != null) {
            fallbackExecutor.shutdownNow();
        }
    }

    @Test
    void successPassesThroughWithoutFallback() {
        fallbackExecutor = newFallbackExecutor(1, 1);
        Fixture<String> fixture = newFixture(AsyncTask.of("pool", "success", () -> "ok"));
        CompletableFuture<String> finalFuture = fixture.pipeline.apply(fixture.context, fixture.command);

        fixture.baseFuture.complete("ok");

        assertEquals("ok", finalFuture.join());
        assertFalse(fixture.events.contains(AsyncTaskStatus.FALLBACK));
    }

    @Test
    void failureTriggersFallbackSuccess() {
        fallbackExecutor = newFallbackExecutor(1, 1);
        AsyncTask<String> task = AsyncTask.<String>of("pool", "fallback-success", () -> "unused")
                .fallback(error -> "fallback-value");
        Fixture<String> fixture = newFixture(task);
        CompletableFuture<String> finalFuture = fixture.pipeline.apply(fixture.context, fixture.command);

        fixture.baseFuture.completeExceptionally(new IllegalStateException("origin"));

        assertEquals("fallback-value", finalFuture.join());
        assertTrue(fixture.events.contains(AsyncTaskStatus.FALLBACK));
        assertTrue(fixture.events.contains(AsyncTaskStatus.FALLBACK_SUCCESS));
    }

    @Test
    void failureTriggersFallbackFailure() {
        fallbackExecutor = newFallbackExecutor(1, 1);
        AsyncTask<String> task = AsyncTask.<String>of("pool", "fallback-failure", () -> "unused")
                .fallback(error -> { throw new IllegalArgumentException("fallback failed"); });
        Fixture<String> fixture = newFixture(task);
        CompletableFuture<String> finalFuture = fixture.pipeline.apply(fixture.context, fixture.command);

        fixture.baseFuture.completeExceptionally(new IllegalStateException("origin"));

        CompletionException error = assertThrows(CompletionException.class, finalFuture::join);
        assertTrue(error.getCause() instanceof AsyncTaskException);
        assertTrue(fixture.events.contains(AsyncTaskStatus.FALLBACK_FAILED));
    }

    @Test
    void fallbackExecutorRejectionCompletesFinalFutureExceptionally() {
        fallbackExecutor = new ThreadPoolExecutor(
                1,
                1,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.AbortPolicy()
        );
        fallbackExecutor.shutdownNow();
        AsyncTask<String> task = AsyncTask.<String>of("pool", "fallback-rejected", () -> "unused")
                .fallback(error -> "fallback");
        Fixture<String> fixture = newFixture(task);
        CompletableFuture<String> finalFuture = fixture.pipeline.apply(fixture.context, fixture.command);

        fixture.baseFuture.completeExceptionally(new IllegalStateException("origin"));

        CompletionException error = assertThrows(CompletionException.class, finalFuture::join);
        assertTrue(error.getCause() instanceof AsyncTaskException);
        assertTrue(fixture.events.contains(AsyncTaskStatus.FALLBACK_FAILED));
    }

    @Test
    void timeoutCompletesBaseFutureAndCanFallback() throws Exception {
        fallbackExecutor = newFallbackExecutor(1, 1);
        AsyncTask<String> task = AsyncTask.<String>of("pool", "timeout", () -> "unused")
                .timeout(Duration.ofMillis(20))
                .fallback(error -> "timeout-fallback");
        Fixture<String> fixture = newFixture(task);
        CompletableFuture<String> finalFuture = fixture.pipeline.apply(fixture.context, fixture.command);

        assertEquals("timeout-fallback", finalFuture.get(1, TimeUnit.SECONDS));
        assertTrue(fixture.events.contains(AsyncTaskStatus.TIMEOUT));
        assertTrue(fixture.events.contains(AsyncTaskStatus.FALLBACK_SUCCESS));
    }

    @Test
    void cancellationDoesNotTriggerFallback() {
        fallbackExecutor = newFallbackExecutor(1, 1);
        AsyncTask<String> task = AsyncTask.<String>of("pool", "cancel", () -> "unused")
                .fallback(error -> "fallback");
        Fixture<String> fixture = newFixture(task);
        CompletableFuture<String> finalFuture = fixture.pipeline.apply(fixture.context, fixture.command);

        fixture.context.getRuntime().tryCancel();
        fixture.baseFuture.cancel(false);

        assertTrue(finalFuture.isCancelled());
        assertFalse(fixture.events.contains(AsyncTaskStatus.FALLBACK));
    }

    @Test
    void queuedFallbackAbortedByShutdownNowCompletesAsFallbackFailed() throws Exception {
        fallbackExecutor = newFallbackExecutor(1, 1);
        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        fallbackExecutor.execute(() -> {
            blockerStarted.countDown();
            try {
                releaseBlocker.await();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(blockerStarted.await(1, TimeUnit.SECONDS));

        AsyncTask<String> task = AsyncTask.<String>of("pool", "fallback-shutdown", () -> "unused")
                .fallback(error -> "fallback");
        Fixture<String> fixture = newFixture(task);
        CompletableFuture<String> finalFuture = fixture.pipeline.apply(fixture.context, fixture.command);

        fixture.baseFuture.completeExceptionally(new IllegalStateException("origin"));
        while (fallbackExecutor.getQueue().isEmpty()) {
            Thread.sleep(5);
        }

        List<Runnable> pending = fallbackExecutor.shutdownNow();
        for (Runnable runnable : pending) {
            if (runnable instanceof ShutdownAbortAware abortAware) {
                abortAware.abortOnShutdown(new RuntimeException("test shutdown"));
            }
        }
        releaseBlocker.countDown();

        CompletionException error = assertThrows(CompletionException.class, finalFuture::join);
        assertTrue(error.getCause() instanceof AsyncTaskException);
        assertTrue(fixture.events.contains(AsyncTaskStatus.FALLBACK_FAILED));
    }

    private ThreadPoolExecutor newFallbackExecutor(int poolSize, int queueCapacity) {
        return new ThreadPoolExecutor(
                poolSize,
                poolSize,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity)
        );
    }

    private <T> Fixture<T> newFixture(AsyncTask<T> task) {
        task.validate();
        TaskDefinition<T> definition = TaskDefinition.from(task);
        CompletableFuture<T> baseFuture = new CompletableFuture<>();
        TaskExecutionRuntime runtime = new TaskExecutionRuntime(TaskResultMode.RESULT_AWARE);
        TaskExecutionContext<T> context = new TaskExecutionContext<>(
                definition,
                definition.getOperation(),
                baseFuture,
                runtime
        );
        RecordingPublisher publisher = new RecordingPublisher();
        AsyncErrorClassifier classifier = new DefaultAsyncErrorClassifier();
        TaskCommand<T> command = new TaskCommand<>(
                context,
                publisher,
                classifier,
                (event, throwable) -> { }
        );
        DefaultTaskResultPipeline pipeline = new DefaultTaskResultPipeline(
                classifier,
                publisher,
                timeoutScheduler,
                fallbackExecutor
        );
        command.submitted();
        return new Fixture<>(context, command, pipeline, baseFuture, publisher.events);
    }

    private record Fixture<T>(
            TaskExecutionContext<T> context,
            TaskCommand<T> command,
            DefaultTaskResultPipeline pipeline,
            CompletableFuture<T> baseFuture,
            List<AsyncTaskStatus> events
    ) {
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
