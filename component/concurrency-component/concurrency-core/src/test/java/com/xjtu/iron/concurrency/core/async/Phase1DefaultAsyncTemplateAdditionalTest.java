package com.xjtu.iron.concurrency.core.async;

import com.xjtu.iron.concurrency.api.execution.template.NamedFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase1 - DefaultAsyncTemplate 补充测试")
class Phase1DefaultAsyncTemplateAdditionalTest {

    private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);
    private final DefaultAsyncTemplate template = new DefaultAsyncTemplate(scheduler);

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    @Test
    @DisplayName("withTimeout 不应污染原始 source Future")
    void withTimeoutShouldNotModifySourceFuture() {
        CompletableFuture<String> source = new CompletableFuture<>();
        CompletableFuture<String> wrapped = template.withTimeout(source, Duration.ofMillis(10));

        ExecutionException error = assertThrows(ExecutionException.class,
                () -> wrapped.get(1, TimeUnit.SECONDS));

        assertInstanceOf(TimeoutException.class, error.getCause());
        assertFalse(source.isDone(), "包装 Future 超时后，原始 source 不应被完成");
        source.complete("late-success");
        assertEquals("late-success", source.join());
    }

    @Test
    @DisplayName("anySuccess 应忽略先失败任务，直到拿到第一个成功")
    void anySuccessShouldIgnoreEarlyFailures() throws Exception {
        CompletableFuture<String> failed = new CompletableFuture<>();
        CompletableFuture<String> success = new CompletableFuture<>();
        CompletableFuture<String> result = template.anySuccess(List.of(failed, success));

        failed.completeExceptionally(new IllegalStateException("bad-source"));
        assertFalse(result.isDone());
        success.complete("ok");

        assertEquals("ok", result.get(1, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("allOfOutcome 应收集部分成功和部分失败")
    void allOfOutcomeShouldCollectSuccessAndFailure() throws Exception {
        CompletableFuture<String> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalArgumentException("bad"));

        var batch = template.allOfOutcome(List.of(
                NamedFuture.of("success", CompletableFuture.completedFuture("ok")),
                NamedFuture.of("failed", failed)
        )).get(1, TimeUnit.SECONDS);

        assertEquals(2, batch.getOutcomes().size());
        assertTrue(batch.getOutcomes().get(0).isSuccess());
        assertFalse(batch.getOutcomes().get(1).isSuccess());
        assertInstanceOf(IllegalArgumentException.class, batch.getOutcomes().get(1).getError());
    }
}
