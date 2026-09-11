package com.xjtu.iron.retry.core.executor;


import com.xjtu.iron.retry.api.backoff.BackoffStrategies;
import com.xjtu.iron.retry.api.backoff.RetryDelaySource;
import com.xjtu.iron.retry.api.event.RetryEvent;
import com.xjtu.iron.retry.api.event.RetryEventType;
import com.xjtu.iron.retry.api.execution.RetryCancellationToken;
import com.xjtu.iron.retry.api.execution.RetryExecution;
import com.xjtu.iron.retry.api.execution.RetryResult;
import com.xjtu.iron.retry.api.execution.RetryStatus;
import com.xjtu.iron.retry.api.policy.RetryDecision;
import com.xjtu.iron.retry.api.policy.RetryFailureCategory;
import com.xjtu.iron.retry.api.policy.RetryPolicy;
import com.xjtu.iron.retry.core.policy.DefaultRetryPolicyRegistry;
import com.xjtu.iron.retry.core.time.RetrySleeper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** 验证同步执行器的主要成功、失败、取消和基础设施边界。 */
class DefaultRetryExecutorTest {

    /** 避免中断标记污染后续测试。 */
    @AfterEach
    void clearInterruptedFlag() {
        Thread.interrupted();
    }

    /** 验证异常两次后第三次成功。 */
    @Test
    void shouldRetryExceptionAndEventuallySucceed() {
        AtomicInteger executions = new AtomicInteger();
        RetryPolicy policy = RetryPolicy.builder("io-retry")
                .maxAttempts(3)
                .maxDuration(Duration.ofSeconds(1))
                .retryOn(
                        RetryFailureCategory.TRANSIENT,
                        "IO",
                        IOException.class
                )
                .build();
        RetryResult<String> result = new DefaultRetryExecutor().execute(
                "query",
                context -> {
                    if (executions.incrementAndGet() < 3) {
                        throw new IOException("temporary failure");
                    }
                    return "OK";
                },
                policy
        );
        assertTrue(result.isSuccess());
        assertEquals("OK", result.getValue());
        assertEquals(3, result.getAttempts());
        assertEquals(3, executions.get());
    }

    /** 验证未匹配异常只执行一次。 */
    @Test
    void shouldStopWhenExceptionIsNotRetryable() {
        RetryPolicy policy = RetryPolicy.builder("io-only")
                .retryOn(IOException.class)
                .build();
        RetryResult<String> result = new DefaultRetryExecutor().execute(
                "validate",
                context -> {
                    throw new IllegalArgumentException("invalid request");
                },
                policy
        );
        assertEquals(RetryStatus.NOT_RETRYABLE, result.getStatus());
        assertEquals(1, result.getAttempts());
        assertInstanceOf(IllegalArgumentException.class, result.getFailure());
    }

    /** 验证决策指定等待时间优先于策略默认退避。 */
    @Test
    void shouldUseDecisionDelayOverrideInsteadOfConfiguredBackoff() {
        List<Duration> sleeps = new ArrayList<>();
        RetrySleeper sleeper = sleeps::add;
        RetryPolicy policy = RetryPolicy.builder("server-delay")
                .maxAttempts(2)
                .maxDuration(Duration.ofSeconds(1))
                .classifier(attempt -> attempt.getAttemptNumber() == 1
                        ? RetryDecision.retryAfter(
                                Duration.ofMillis(123),
                                RetryDelaySource.SERVER_DIRECTED,
                                "Retry-After",
                                "HTTP_429",
                                RetryFailureCategory.THROTTLING)
                        : RetryDecision.success("done"))
                .backoffStrategy(BackoffStrategies.fixed(Duration.ofMillis(999)))
                .build();
        DefaultRetryExecutor executor = new DefaultRetryExecutor(
                new DefaultRetryPolicyRegistry(),
                List.of(),
                sleeper
        );
        RetryResult<String> result = executor.execute(
                "call",
                context -> "value",
                policy
        );
        assertEquals(RetryStatus.SUCCESS, result.getStatus());
        assertEquals(List.of(Duration.ofMillis(123)), sleeps);
    }

    /** 验证达到最大尝试次数后返回 EXHAUSTED。 */
    @Test
    void shouldReturnExhaustedAfterMaximumAttempts() {
        RetryPolicy policy = RetryPolicy.builder("always-fail")
                .maxAttempts(3)
                .maxDuration(Duration.ofSeconds(1))
                .retryOn(IOException.class)
                .build();
        RetryResult<String> result = new DefaultRetryExecutor().execute(
                "remote",
                context -> {
                    throw new IOException("still unavailable");
                },
                policy
        );
        assertEquals(RetryStatus.EXHAUSTED, result.getStatus());
        assertEquals(3, result.getAttempts());
        assertTrue(result.isExhausted());
    }

    /** 验证退避超过剩余预算时不再真正休眠。 */
    @Test
    void shouldTimeoutBeforeBackoffExceedsRemainingDuration() {
        AtomicInteger sleepCalls = new AtomicInteger();
        RetrySleeper sleeper = duration -> sleepCalls.incrementAndGet();
        RetryPolicy policy = RetryPolicy.builder("short-budget")
                .maxAttempts(3)
                .maxDuration(Duration.ofMillis(10))
                .retryOn(IOException.class)
                .backoffStrategy(BackoffStrategies.fixed(Duration.ofMillis(50)))
                .build();
        DefaultRetryExecutor executor = new DefaultRetryExecutor(
                new DefaultRetryPolicyRegistry(),
                List.of(),
                sleeper
        );
        RetryResult<String> result = executor.execute(
                "remote",
                context -> {
                    throw new IOException("temporary");
                },
                policy
        );
        assertEquals(RetryStatus.TIMED_OUT, result.getStatus());
        assertEquals(1, result.getAttempts());
        assertEquals(0, sleepCalls.get());
    }

    /** 验证等待中断会恢复线程中断标记。 */
    @Test
    void shouldRestoreInterruptedFlagWhenBackoffIsInterrupted() {
        RetrySleeper sleeper = duration -> {
            throw new InterruptedException("interrupted while waiting");
        };
        RetryPolicy policy = RetryPolicy.builder("interruptible")
                .maxAttempts(3)
                .retryOn(IOException.class)
                .backoffStrategy(BackoffStrategies.fixed(Duration.ofMillis(1)))
                .build();
        DefaultRetryExecutor executor = new DefaultRetryExecutor(
                new DefaultRetryPolicyRegistry(),
                List.of(),
                sleeper
        );
        RetryResult<String> result = executor.execute(
                "remote",
                context -> {
                    throw new IOException("temporary");
                },
                policy
        );
        assertEquals(RetryStatus.INTERRUPTED, result.getStatus());
        assertTrue(Thread.currentThread().isInterrupted());
    }

    /** 验证调用方可以在第一次失败后阻止第二次尝试。 */
    @Test
    void shouldCancelBeforeNextAttempt() {
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicInteger executions = new AtomicInteger();
        RetryPolicy policy = RetryPolicy.builder("cancel")
                .maxAttempts(3)
                .retryOn(IOException.class)
                .build();
        RetryExecution<String> execution = RetryExecution
                .<String>builder(
                        "cancel-operation",
                        context -> {
                            executions.incrementAndGet();
                            cancelled.set(true);
                            throw new IOException("temporary");
                        },
                        policy
                )
                .cancellationToken(RetryCancellationToken.from(cancelled))
                .retryId("business-request-id")
                .build();
        RetryResult<String> result = new DefaultRetryExecutor().execute(execution);
        assertEquals(RetryStatus.CANCELLED, result.getStatus());
        assertEquals("business-request-id", result.getRetryId());
        assertEquals(1, result.getAttempts());
        assertEquals(1, executions.get());
    }

    /** 验证初始已取消时一次业务操作都不执行。 */
    @Test
    void shouldCancelBeforeFirstAttempt() {
        AtomicInteger executions = new AtomicInteger();
        RetryPolicy policy = RetryPolicy.builder("cancelled-before-start").build();
        RetryExecution<String> execution = RetryExecution
                .builder(
                        "cancel-before-start",
                        context -> {
                            executions.incrementAndGet();
                            return "unexpected";
                        },
                        policy
                )
                .cancellationToken(() -> true)
                .build();
        RetryResult<String> result = new DefaultRetryExecutor().execute(execution);
        assertEquals(RetryStatus.CANCELLED, result.getStatus());
        assertEquals(0, result.getAttempts());
        assertEquals(0, executions.get());
    }

    /** 验证分类器不能吞掉业务异常并宣告成功。 */
    @Test
    void shouldRejectClassifierThatMarksFailureAsSuccess() {
        List<RetryEvent> events = new ArrayList<>();
        RetryPolicy policy = RetryPolicy.builder("invalid-classifier")
                .classifier(attempt -> RetryDecision.success("incorrect"))
                .build();
        DefaultRetryExecutor executor = new DefaultRetryExecutor(
                new DefaultRetryPolicyRegistry(),
                List.of(events::add)
        );
        RetryResult<String> result = executor.execute(
                "invalid-classifier-operation",
                context -> {
                    throw new IOException("failure");
                },
                policy
        );
        assertEquals(RetryStatus.EXECUTION_FAILED, result.getStatus());
        long terminalEvents = events.stream()
                .filter(event -> event.getFinalStatus() != null)
                .count();
        assertEquals(1L, terminalEvents);
    }

    /** 验证一个监听器失败不会影响其他监听器和业务结果。 */
    @Test
    void shouldIsolateListenerFailureAndContinueExecution() {
        List<RetryEvent> events = new ArrayList<>();
        RetryPolicy policy = RetryPolicy.builder("events")
                .maxAttempts(2)
                .retryOn(IOException.class)
                .build();
        DefaultRetryExecutor executor = new DefaultRetryExecutor(
                new DefaultRetryPolicyRegistry(),
                List.of(
                        event -> {
                            throw new IllegalStateException("listener failed");
                        },
                        events::add
                )
        );
        RetryResult<String> result = executor.execute(
                "event-operation",
                context -> {
                    throw new IOException("failure");
                },
                policy
        );
        assertEquals(RetryStatus.EXHAUSTED, result.getStatus());
        assertFalse(events.isEmpty());
        assertEquals(
                RetryEventType.EXECUTION_STARTED,
                events.get(0).getEventType()
        );
        assertEquals(
                RetryEventType.EXECUTION_EXHAUSTED,
                events.get(events.size() - 1).getEventType()
        );
    }


}
