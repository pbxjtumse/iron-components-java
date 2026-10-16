package com.xjtu.iron.concurrency.core.async;

import com.xjtu.iron.concurrency.api.error.CompletableFutureExceptionUtils;
import com.xjtu.iron.concurrency.api.execution.template.AsyncBatchResult;
import com.xjtu.iron.concurrency.api.execution.template.AsyncTaskOutcome;
import com.xjtu.iron.concurrency.api.execution.template.AsyncTemplate;
import com.xjtu.iron.concurrency.api.execution.template.NamedFuture;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * 默认 CompletableFuture 编排模板。
 */
public class DefaultAsyncTemplate implements AsyncTemplate {

    /**
     * 通用 Future 超时调度器。
     *
     * <p>
     * 只负责完成包装 Future 的超时结果，不负责执行业务逻辑。
     * </p>
     */
    private final ScheduledExecutorService timeoutScheduler;

    public DefaultAsyncTemplate(ScheduledExecutorService timeoutScheduler) {
        this.timeoutScheduler = Objects.requireNonNull(timeoutScheduler, "timeoutScheduler must not be null");
    }

    @Override
    public <T> CompletableFuture<List<T>> allOf(Collection<CompletableFuture<T>> futures) {
        CompletableFuture<?>[] array = futures.toArray(new CompletableFuture[0]);

        return CompletableFuture.allOf(array)
                        .thenApply(ignored -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }

    @Override
    public <T> CompletableFuture<AsyncBatchResult<T>> allOfOutcome(Collection<NamedFuture<T>> futures) {
        List<CompletableFuture<AsyncTaskOutcome<T>>> wrapped = new ArrayList<>();

        for (NamedFuture<T> namedFuture : futures) {
            CompletableFuture<AsyncTaskOutcome<T>> outcomeFuture =
                    namedFuture.getFuture()
                            .thenApply(value -> AsyncTaskOutcome.success(namedFuture.getTaskName(), value))
                            .exceptionally(ex -> AsyncTaskOutcome.failure(namedFuture.getTaskName(), CompletableFutureExceptionUtils.unwrap(ex)));
            wrapped.add(outcomeFuture);
        }

        CompletableFuture<?>[] array = wrapped.toArray(new CompletableFuture[0]);

        return CompletableFuture.allOf(array)
                .thenApply(ignored -> {
                    List<AsyncTaskOutcome<T>> outcomes = wrapped.stream()
                            .map(CompletableFuture::join)
                            .toList();

                    return new AsyncBatchResult<>(outcomes);
                });
    }

    @Override
    public <T> CompletableFuture<List<T>> allOfFailFast(Collection<CompletableFuture<T>> futures) {
        CompletableFuture<List<T>> result = new CompletableFuture<>();
        for (CompletableFuture<T> future : futures) {
            future.whenComplete((value, error) -> {
                if (error != null && !result.isDone()) {
                    result.completeExceptionally(CompletableFutureExceptionUtils.unwrap(error));
                    /*
                     * 注意：
                     * cancel(true) 对 CompletableFuture.supplyAsync 提交的任务不一定能中断底层线程。
                     * 这里只是尽力取消尚未完成的 Future。
                     */
                    for (CompletableFuture<T> other : futures) {
                        other.cancel(true);
                    }
                }
            });
        }
        CompletableFuture<?>[] array = futures.toArray(new CompletableFuture[0]);
        CompletableFuture.allOf(array)
                .thenApply(ignored -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList())
                .whenComplete((values, error) -> {
                    if (error != null) {
                        result.completeExceptionally(CompletableFutureExceptionUtils.unwrap(error));
                    } else {
                        result.complete(values);
                    }
                });
        return result;
    }

    @Override
    public <T> CompletableFuture<T> anyOf(Collection<CompletableFuture<T>> futures) {
        if (futures == null || futures.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("futures must not be empty"));
        }

        CompletableFuture<T> result = new CompletableFuture<>();
        for (CompletableFuture<T> future : futures) {
            future.whenComplete((value, error) -> {
                if (error == null) {
                    result.complete(value);
                } else {
                    result.completeExceptionally(CompletableFutureExceptionUtils.unwrap(error));
                }
            });
        }

        return result;
    }

    @Override
    public <T> CompletableFuture<T> anySuccess(Collection<CompletableFuture<T>> futures) {
        if (futures == null || futures.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("futures must not be empty"));
        }

        CompletableFuture<T> result = new CompletableFuture<>();
        java.util.concurrent.atomic.AtomicInteger failureCount = new java.util.concurrent.atomic.AtomicInteger(0);
        List<Throwable> errors = new java.util.concurrent.CopyOnWriteArrayList<>();
        int total = futures.size();

        for (CompletableFuture<T> future : futures) {
            future.whenComplete((value, error) -> {
                if (error == null) {
                    result.complete(value);
                    return;
                }

                errors.add(CompletableFutureExceptionUtils.unwrap(error));
                if (failureCount.incrementAndGet() == total) {
                    RuntimeException allFailed = new RuntimeException("All futures failed");
                    for (Throwable throwable : errors) {
                        allFailed.addSuppressed(throwable);
                    }
                    result.completeExceptionally(allFailed);
                }
            });
        }

        return result;
    }

    @Override
    public <T> CompletableFuture<T> withTimeout(CompletableFuture<T> source, Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return source;
        }

        CompletableFuture<T> result = new CompletableFuture<>();

        ScheduledFuture<?> timeoutFuture = timeoutScheduler.schedule(() -> result.completeExceptionally(
                                new TimeoutException("Future timeout after " + timeout.toMillis() + " ms")),
                        timeout.toMillis(),
                        TimeUnit.MILLISECONDS
                );

        source.whenComplete((value, throwable) -> {
            boolean won;

            if (throwable == null) {
                won = result.complete(value);
            } else {
                won = result.completeExceptionally(
                        CompletableFutureExceptionUtils.unwrap(throwable)
                );
            }

            if (won) {
                timeoutFuture.cancel(false);
            }
        });

        return result;
    }

    @Override
    public <T> CompletableFuture<T> withFallback(
            CompletableFuture<T> future,
            Function<Throwable, T> fallback
    ) {
        if (fallback == null) {
            return future;
        }

        return future.exceptionally(error -> fallback.apply(CompletableFutureExceptionUtils.unwrap(error)));
    }

}