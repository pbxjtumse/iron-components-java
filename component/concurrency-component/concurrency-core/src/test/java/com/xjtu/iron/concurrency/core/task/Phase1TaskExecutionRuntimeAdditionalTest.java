package com.xjtu.iron.concurrency.core.task;

import com.xjtu.iron.concurrency.api.enums.task.AsyncTaskStatus;
import com.xjtu.iron.concurrency.api.task.TaskResultMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase1 - TaskExecutionRuntime 并发竞争补充测试")
class Phase1TaskExecutionRuntimeAdditionalTest {

    @Test
    @DisplayName("SUCCESS 与 TIMEOUT 并发竞争时只能一个赢")
    void successAndTimeoutRaceShouldResolveOnlyOnce() throws Exception {
        TaskExecutionRuntime runtime = new TaskExecutionRuntime(TaskResultMode.RESULT_AWARE);
        runtime.tryMarkSubmitted();
        runtime.tryMarkRunning();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Set<AsyncTaskStatus> winners = ConcurrentHashMap.newKeySet();

        try {
            executor.submit(() -> {
                await(start);
                if (runtime.tryResolveBaseOutcome(AsyncTaskStatus.SUCCESS)) {
                    winners.add(AsyncTaskStatus.SUCCESS);
                }
            });
            executor.submit(() -> {
                await(start);
                if (runtime.tryResolveBaseOutcome(AsyncTaskStatus.TIMEOUT)) {
                    winners.add(AsyncTaskStatus.TIMEOUT);
                }
            });
            start.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, winners.size());
        assertTrue(winners.contains(runtime.getStatus()));
    }

    @Test
    @DisplayName("CANCELLED 后不能再进入 SUBMITTED / RUNNING / SUCCESS")
    void cancelledShouldNotBecomeSubmittedRunningOrSuccess() {
        TaskExecutionRuntime runtime = new TaskExecutionRuntime(TaskResultMode.RESULT_AWARE);

        assertTrue(runtime.tryCancel());

        assertFalse(runtime.tryMarkSubmitted());
        assertFalse(runtime.tryMarkRunning());
        assertFalse(runtime.tryResolveBaseOutcome(AsyncTaskStatus.SUCCESS));
        assertEquals(AsyncTaskStatus.CANCELLED, runtime.getStatus());
        assertTrue(runtime.isBaseOutcomeResolved());
        assertTrue(runtime.isFinalOutcomeResolved());
    }

    @Test
    @DisplayName("FALLBACK_SUCCESS 与 FALLBACK_FAILED 只能一个成为最终结果")
    void fallbackFinalOutcomeShouldResolveOnlyOnce() {
        TaskExecutionRuntime runtime = new TaskExecutionRuntime(TaskResultMode.RESULT_AWARE);
        runtime.tryResolveBaseOutcome(AsyncTaskStatus.FAILED);
        runtime.markIntermediate(AsyncTaskStatus.FALLBACK);

        assertTrue(runtime.tryFinalize(AsyncTaskStatus.FALLBACK_SUCCESS));
        assertFalse(runtime.tryFinalize(AsyncTaskStatus.FALLBACK_FAILED));

        assertEquals(AsyncTaskStatus.FALLBACK_SUCCESS, runtime.getStatus());
        assertTrue(runtime.isFinalOutcomeResolved());
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
