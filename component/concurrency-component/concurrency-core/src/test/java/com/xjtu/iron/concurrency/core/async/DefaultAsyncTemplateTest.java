package com.xjtu.iron.concurrency.core.async;

import com.xjtu.iron.concurrency.api.execution.template.AsyncBatchResult;
import com.xjtu.iron.concurrency.api.execution.template.NamedFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class DefaultAsyncTemplateTest {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private final DefaultAsyncTemplate template = new DefaultAsyncTemplate(scheduler);

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    @Test
    void allOfReturnsValuesInInputOrder() {
        CompletableFuture<List<Integer>> result = template.allOf(List.of(
                CompletableFuture.completedFuture(1),
                CompletableFuture.completedFuture(2)
        ));

        assertEquals(List.of(1, 2), result.join());
    }

    @Test
    void allOfOutcomeCollectsSuccessAndFailure() {
        CompletableFuture<Integer> success = CompletableFuture.completedFuture(1);
        CompletableFuture<Integer> failure = CompletableFuture.failedFuture(new IllegalStateException("bad"));

        AsyncBatchResult<Integer> result = template.allOfOutcome(List.of(
                NamedFuture.of("success", success),
                NamedFuture.of("failure", failure)
        )).join();

        assertFalse(result.isAllSuccess());
        assertTrue(result.hasFailure());
        assertEquals(List.of(1), result.successValues());
        assertEquals(1, result.failures().size());
        assertEquals("failure", result.failures().get(0).getTaskName());
    }

    @Test
    void allOfFailFastCompletesExceptionallyAndCancelsOthers() {
        CompletableFuture<Integer> slow = new CompletableFuture<>();
        CompletableFuture<Integer> failed = new CompletableFuture<>();

        CompletableFuture<List<Integer>> result = template.allOfFailFast(List.of(slow, failed));
        failed.completeExceptionally(new IllegalArgumentException("boom"));

        CompletionException error = assertThrows(CompletionException.class, result::join);
        assertTrue(error.getCause() instanceof IllegalArgumentException);
        assertTrue(slow.isCancelled());
    }

    @Test
    void anyOfReturnsFirstSuccessOrFirstFailure() {
        CompletableFuture<Integer> first = new CompletableFuture<>();
        CompletableFuture<Integer> second = new CompletableFuture<>();

        CompletableFuture<Integer> result = template.anyOf(List.of(first, second));
        second.complete(2);

        assertEquals(2, result.join());
    }

    @Test
    void anyOfFailsFastOnFirstFailure() {
        CompletableFuture<Integer> first = new CompletableFuture<>();
        CompletableFuture<Integer> second = new CompletableFuture<>();

        CompletableFuture<Integer> result = template.anyOf(List.of(first, second));
        first.completeExceptionally(new IllegalStateException("bad"));

        CompletionException error = assertThrows(CompletionException.class, result::join);
        assertTrue(error.getCause() instanceof IllegalStateException);
    }

    @Test
    void anySuccessReturnsFirstSuccessAndFailsWhenAllFailed() {
        CompletableFuture<Integer> failed = CompletableFuture.failedFuture(new IllegalStateException("bad"));
        CompletableFuture<Integer> success = CompletableFuture.completedFuture(7);

        assertEquals(7, template.anySuccess(List.of(failed, success)).join());

        CompletableFuture<Integer> allFailed = template.anySuccess(List.of(
                CompletableFuture.failedFuture(new IllegalArgumentException("a")),
                CompletableFuture.failedFuture(new IllegalStateException("b"))
        ));

        CompletionException error = assertThrows(CompletionException.class, allFailed::join);
        assertEquals("All futures failed", error.getCause().getMessage());
        assertEquals(2, error.getCause().getSuppressed().length);
    }

    @Test
    void withTimeoutIsNonIntrusiveToSourceFuture() throws Exception {
        CompletableFuture<String> source = new CompletableFuture<>();
        CompletableFuture<String> timed = template.withTimeout(source, Duration.ofMillis(20));

        assertNotSame(source, timed);
        Thread.sleep(60);

        assertFalse(source.isDone(), "source future must not be modified by timeout wrapper");
        CompletionException error = assertThrows(CompletionException.class, timed::join);
        assertTrue(error.getCause() instanceof TimeoutException);

        source.complete("real");
        assertEquals("real", source.join());
    }

    @Test
    void withTimeoutCancelsTimerWhenSourceCompletesFirst() {
        CompletableFuture<String> source = new CompletableFuture<>();
        CompletableFuture<String> timed = template.withTimeout(source, Duration.ofSeconds(5));

        source.complete("ok");

        assertEquals("ok", timed.join());
    }

    @Test
    void withFallbackReturnsOriginalWhenFallbackMissing() {
        CompletableFuture<String> source = CompletableFuture.failedFuture(new RuntimeException("bad"));

        assertSame(source, template.withFallback(source, null));
    }

    @Test
    void withFallbackRecoversException() {
        CompletableFuture<String> source = CompletableFuture.failedFuture(new RuntimeException("bad"));

        assertEquals("fallback", template.withFallback(source, error -> "fallback").join());
    }
}
