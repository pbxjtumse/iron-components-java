package com.xjtu.iron.concurrency.core.rejection;

import com.xjtu.iron.concurrency.core.task.CallerRunsAware;
import com.xjtu.iron.concurrency.core.task.RejectedTaskAware;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RejectedExecutionHandlersTest {

    private ThreadPoolExecutor executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void abortRejectsAndThrows() {
        executor = newExecutor(1);
        RecordingTask task = new RecordingTask();

        assertThrows(RejectedExecutionException.class,
                () -> new AwareAbortRejectedExecutionHandler().rejectedExecution(task, executor));

        assertTrue(task.rejected.get());
        assertFalse(task.executed.get());
    }

    @Test
    void callerRunsExecutesInSubmittingThreadWhenExecutorRunning() {
        executor = newExecutor(1);
        RecordingTask task = new RecordingTask();
        String callerThread = Thread.currentThread().getName();

        new CallerRunsRejectedExecutionHandler().rejectedExecution(task, executor);

        assertTrue(task.callerThreadMarked.get());
        assertTrue(task.executed.get());
        assertFalse(task.rejected.get());
        assertEquals(callerThread, task.executionThread.get());
    }

    @Test
    void callerRunsRejectsWhenExecutorShutdown() {
        executor = newExecutor(1);
        executor.shutdownNow();
        RecordingTask task = new RecordingTask();

        assertThrows(RejectedExecutionException.class,
                () -> new CallerRunsRejectedExecutionHandler().rejectedExecution(task, executor));

        assertTrue(task.rejected.get());
        assertFalse(task.callerThreadMarked.get());
        assertFalse(task.executed.get());
    }

    @Test
    void discardIsRejectAwareButDoesNotThrow() {
        executor = newExecutor(1);
        RecordingTask task = new RecordingTask();

        new DiscardRejectedExecutionHandler().rejectedExecution(task, executor);

        assertTrue(task.rejected.get());
        assertFalse(task.executed.get());
    }

    @Test
    void discardOldestRejectsOldQueuedTaskAndQueuesCurrentTask() {
        executor = newExecutor(1);
        RecordingTask oldest = new RecordingTask();
        RecordingTask current = new RecordingTask();

        assertTrue(executor.getQueue().offer(oldest));

        new DiscardOldestRejectedExecutionHandler().rejectedExecution(current, executor);

        assertTrue(oldest.rejected.get());
        assertFalse(current.rejected.get());
        assertSame(current, executor.getQueue().peek());
    }

    @Test
    void discardOldestRejectsCurrentWhenExecutorShutdown() {
        executor = newExecutor(1);
        executor.shutdownNow();
        RecordingTask current = new RecordingTask();

        assertThrows(RejectedExecutionException.class,
                () -> new DiscardOldestRejectedExecutionHandler().rejectedExecution(current, executor));

        assertTrue(current.rejected.get());
    }

    @Test
    void blockingWaitEnqueuesWhenSpaceBecomesAvailable() throws Exception {
        executor = newExecutor(1);
        RecordingTask task = new RecordingTask();

        Thread releaser = new Thread(() -> {
            try {
                Thread.sleep(30);
                executor.getQueue().poll();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(executor.getQueue().offer(new RecordingTask()));
        releaser.start();

        new BlockingWaitRejectedExecutionHandler(Duration.ofMillis(300)).rejectedExecution(task, executor);

        assertFalse(task.rejected.get());
        assertTrue(executor.getQueue().contains(task));
        releaser.join(1000);
    }

    @Test
    void blockingWaitRejectsOnTimeout() {
        executor = newExecutor(1);
        RecordingTask task = new RecordingTask();
        assertTrue(executor.getQueue().offer(new RecordingTask()));

        assertThrows(RejectedExecutionException.class,
                () -> new BlockingWaitRejectedExecutionHandler(Duration.ofMillis(10)).rejectedExecution(task, executor));

        assertTrue(task.rejected.get());
    }

    @Test
    void blockingWaitRejectsWhenInterrupted() {
        executor = newExecutor(1);
        RecordingTask task = new RecordingTask();
        assertTrue(executor.getQueue().offer(new RecordingTask()));

        Thread.currentThread().interrupt();
        try {
            assertThrows(RejectedExecutionException.class,
                    () -> new BlockingWaitRejectedExecutionHandler(Duration.ofSeconds(1)).rejectedExecution(task, executor));
            assertTrue(task.rejected.get());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void rejectSupportDoesNotSwallowErrors() {
        AssertionError error = assertThrows(AssertionError.class,
                () -> RejectedTaskSupport.reject(new ErrorTask(), "boom"));

        assertEquals("fatal", error.getMessage());
    }

    @Test
    void rejectSupportPreservesNotificationRuntimeExceptionAsSuppressed() {
        RejectedExecutionException error = RejectedTaskSupport.reject(new RuntimeErrorTask(), "boom");

        assertEquals(1, error.getSuppressed().length);
        assertTrue(error.getSuppressed()[0] instanceof IllegalStateException);
    }

    private ThreadPoolExecutor newExecutor(int queueCapacity) {
        return new ThreadPoolExecutor(
                1,
                1,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity)
        );
    }

    private static final class RecordingTask implements Runnable, CallerRunsAware, RejectedTaskAware {
        private final AtomicBoolean callerThreadMarked = new AtomicBoolean(false);
        private final AtomicBoolean executed = new AtomicBoolean(false);
        private final AtomicBoolean rejected = new AtomicBoolean(false);
        private final AtomicReference<String> executionThread = new AtomicReference<>();

        @Override
        public void markCallerThreadExecution() {
            callerThreadMarked.set(true);
        }

        @Override
        public void reject(Throwable throwable) {
            rejected.set(true);
        }

        @Override
        public void run() {
            executed.set(true);
            executionThread.set(Thread.currentThread().getName());
        }
    }

    private static final class ErrorTask implements Runnable, RejectedTaskAware {
        @Override
        public void reject(Throwable throwable) {
            throw new AssertionError("fatal");
        }

        @Override
        public void run() {
        }
    }

    private static final class RuntimeErrorTask implements Runnable, RejectedTaskAware {
        @Override
        public void reject(Throwable throwable) {
            throw new IllegalStateException("listener failed");
        }

        @Override
        public void run() {
        }
    }
}
