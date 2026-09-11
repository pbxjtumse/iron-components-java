package com.xjtu.iron.concurrency.core.control;

import com.xjtu.iron.concurrency.api.enums.task.AsyncTaskStatus;
import com.xjtu.iron.concurrency.api.execution.task.TaskCancelResult;
import com.xjtu.iron.concurrency.api.task.TaskResultMode;
import com.xjtu.iron.concurrency.core.testfixture.Phase1TestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase1 - TaskControl / Cancellation 补充测试")
class Phase1TaskControlCancellationAdditionalTest {

    @Test
    @DisplayName("cancel queued task 应从队列移除，operation 不执行")
    void cancelQueuedTaskShouldRemoveFromQueueAndNotExecuteOperation() throws Exception {
        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        AtomicBoolean executed = new AtomicBoolean(false);
        ThreadPoolExecutor executor = singleThreadExecutorWithQueue(1);
        try {
            executor.execute(() -> {
                blockerStarted.countDown();
                await(releaseBlocker);
            });
            assertTrue(blockerStarted.await(1, TimeUnit.SECONDS));

            var fixture = Phase1TestSupport.commandFixture(
                    "cancel-queued",
                    () -> {
                        executed.set(true);
                        return "should-not-run";
                    },
                    TaskResultMode.RESULT_AWARE
            );
            fixture.command().submitted();
            executor.execute(fixture.command());
            assertTrue(executor.getQueue().contains(fixture.command()));

            TaskControl<String> control = new TaskControl<>(executor, fixture.command(), fixture.baseFuture());
            TaskCancelResult result = control.cancel(true);

            assertEquals(TaskCancelResult.CANCELLED, result);
            assertFalse(executor.getQueue().contains(fixture.command()));
            assertFalse(executed.get());
            assertEquals(AsyncTaskStatus.CANCELLED, fixture.runtime().getStatus());
            assertTrue(fixture.baseFuture().isCancelled());
        } finally {
            releaseBlocker.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("cancel running task with interrupt 应中断运行线程")
    void cancelRunningTaskWithInterruptShouldInterruptThread() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ThreadPoolExecutor executor = singleThreadExecutorWithQueue(1);
        try {
            var fixture = Phase1TestSupport.commandFixture(
                    "cancel-running",
                    () -> {
                        started.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException e) {
                            interrupted.countDown();
                            Thread.currentThread().interrupt();
                        }
                        return "done";
                    },
                    TaskResultMode.RESULT_AWARE
            );
            fixture.command().submitted();
            executor.execute(fixture.command());
            assertTrue(started.await(1, TimeUnit.SECONDS));

            TaskControl<String> control = new TaskControl<>(executor, fixture.command(), fixture.baseFuture());
            assertEquals(TaskCancelResult.CANCELLED, control.cancel(true));

            assertTrue(interrupted.await(1, TimeUnit.SECONDS));
            assertEquals(AsyncTaskStatus.CANCELLED, fixture.runtime().getStatus());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("DefaultTaskCancellationManager 找不到 taskId 时返回 NOT_FOUND")
    void cancellationManagerShouldReturnNotFoundWhenTaskMissing() {
        DefaultTaskCancellationManager manager = new DefaultTaskCancellationManager(new DefaultTaskControlRegistry());

        assertEquals(TaskCancelResult.NOT_FOUND, manager.cancel("missing", true));
        assertFalse(manager.isCancellable("missing"));
    }

    @Test
    @DisplayName("TaskControlRegistry 不允许同一个 taskId 覆盖已有运行任务")
    void taskControlRegistryShouldRejectDuplicateTaskId() {
        DefaultTaskControlRegistry registry = new DefaultTaskControlRegistry();
        registry.register("task-1", mayInterrupt -> TaskCancelResult.CANCELLED);

        assertThrows(IllegalStateException.class,
                () -> registry.register("task-1", mayInterrupt -> TaskCancelResult.CANCELLED));
    }

    private static ThreadPoolExecutor singleThreadExecutorWithQueue(int queueCapacity) {
        return new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new java.util.concurrent.ArrayBlockingQueue<>(queueCapacity)
        );
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
