package com.xjtu.iron.concurrency.core.rejection;

import com.xjtu.iron.concurrency.api.enums.task.AsyncTaskStatus;
import com.xjtu.iron.concurrency.api.task.TaskExecutionMode;
import com.xjtu.iron.concurrency.api.task.TaskResultMode;
import com.xjtu.iron.concurrency.core.testfixture.Phase1TestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase1 - 拒绝策略补充测试")
class Phase1RejectedExecutionHandlersAdditionalTest {

    @Test
    @DisplayName("DISCARD 不抛异常，但必须把 command 标记为 REJECTED")
    void discardShouldRejectAwareCommandWithoutThrowing() {
        var fixture = Phase1TestSupport.commandFixture("reject-discard", () -> "ok", TaskResultMode.RESULT_AWARE);
        ThreadPoolExecutor executor = executor();
        try {
            fixture.command().submitted();

            assertDoesNotThrow(() -> new DiscardRejectedExecutionHandler().rejectedExecution(fixture.command(), executor));

            assertTrue(fixture.command().isRejected());
            assertEquals(AsyncTaskStatus.REJECTED, fixture.runtime().getStatus());
            assertTrue(fixture.baseFuture().isCompletedExceptionally());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("CALLER_RUNS 应在调用线程执行且不标记 REJECTED")
    void callerRunsShouldExecuteInSubmittingThreadAndNotReject() throws Exception {
        AtomicReference<Thread> executedThread = new AtomicReference<>();
        Thread submittingThread = Thread.currentThread();
        var fixture = Phase1TestSupport.commandFixture(
                "reject-caller-runs",
                () -> {
                    executedThread.set(Thread.currentThread());
                    return "ok";
                },
                TaskResultMode.RESULT_AWARE
        );
        ThreadPoolExecutor executor = executor();
        try {
            fixture.command().submitted();

            new CallerRunsRejectedExecutionHandler().rejectedExecution(fixture.command(), executor);

            assertSame(submittingThread, executedThread.get());
            assertEquals(TaskExecutionMode.CALLER_THREAD, fixture.runtime().getExecutionMode());
            assertEquals(AsyncTaskStatus.SUCCESS, fixture.runtime().getStatus());
            assertEquals("ok", fixture.baseFuture().get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("executor shutdown 时 CALLER_RUNS 应拒绝任务")
    void callerRunsShouldRejectWhenExecutorShutdown() {
        var fixture = Phase1TestSupport.commandFixture("reject-caller-shutdown", () -> "ok", TaskResultMode.RESULT_AWARE);
        ThreadPoolExecutor executor = executor();
        executor.shutdownNow();
        fixture.command().submitted();

        assertThrows(RejectedExecutionException.class,
                () -> new CallerRunsRejectedExecutionHandler().rejectedExecution(fixture.command(), executor));
        assertEquals(AsyncTaskStatus.REJECTED, fixture.runtime().getStatus());
    }

    @Test
    @DisplayName("BLOCKING_WAIT 等待超时后应拒绝并完成 Future")
    void blockingWaitShouldRejectWhenTimeout() {
        var fixture = Phase1TestSupport.commandFixture("reject-blocking", () -> "ok", TaskResultMode.RESULT_AWARE);
        ThreadPoolExecutor executor = executor();
        try {
            executor.getQueue().offer(() -> { });
            fixture.command().submitted();

            assertThrows(RejectedExecutionException.class,
                    () -> new BlockingWaitRejectedExecutionHandler(Duration.ofMillis(5))
                            .rejectedExecution(fixture.command(), executor));

            assertEquals(AsyncTaskStatus.REJECTED, fixture.runtime().getStatus());
            assertTrue(fixture.baseFuture().isCompletedExceptionally());
        } finally {
            executor.shutdownNow();
        }
    }

    private static ThreadPoolExecutor executor() {
        return new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1)
        );
    }
}
