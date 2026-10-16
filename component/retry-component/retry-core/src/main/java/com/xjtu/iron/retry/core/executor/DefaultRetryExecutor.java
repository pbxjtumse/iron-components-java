package com.xjtu.iron.retry.core.executor;

import com.xjtu.iron.foundation.core.exception.IronExceptions;
import com.xjtu.iron.foundation.core.validation.Arguments;
import com.xjtu.iron.foundation.id.api.StringIdGenerator;
import com.xjtu.iron.foundation.id.factory.IdGenerators;
import com.xjtu.iron.retry.api.backoff.BackoffStrategy;
import com.xjtu.iron.retry.api.backoff.RetryDelay;
import com.xjtu.iron.retry.api.event.RetryEvent;
import com.xjtu.iron.retry.api.event.RetryEventType;
import com.xjtu.iron.retry.api.event.RetryListener;
import com.xjtu.iron.retry.api.execution.*;
import com.xjtu.iron.retry.api.policy.*;
import com.xjtu.iron.retry.core.policy.DefaultRetryPolicyRegistry;
import com.xjtu.iron.retry.core.time.RetryClock;
import com.xjtu.iron.retry.core.time.RetrySleeper;
import com.xjtu.iron.retry.core.time.SystemRetryClock;
import com.xjtu.iron.retry.core.time.ThreadSleepRetrySleeper;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 实现进程内同步有限重试。
 *
 * <p>该实现只捕获 Exception，不捕获 Error；总时长预算也不会强制终止正在运行的同步业务代码。</p>
 */
public final class DefaultRetryExecutor implements RetryExecutor {

    /** 命名策略注册表。 */
    private final RetryPolicyRegistry retryPolicyRegistry;
    /** 生命周期事件分发器。 */
    private final RetryEventDispatcher eventDispatcher;
    /** 同步退避等待实现。 */
    private final RetrySleeper retrySleeper;
    /** 墙上时间和单调时间来源。 */
    private final RetryClock retryClock;
    /** 逻辑执行标识生成器。 */
    private final StringIdGenerator retryIdGenerator;

    public DefaultRetryExecutor() {
        this(
                new DefaultRetryPolicyRegistry(),
                Collections.emptyList(),
                new ThreadSleepRetrySleeper(),
                new SystemRetryClock(),
                IdGenerators.uuidV7()
        );
    }

    public DefaultRetryExecutor(
            RetryPolicyRegistry retryPolicyRegistry,
            List<RetryListener> retryListeners) {
        this(
                retryPolicyRegistry,
                retryListeners,
                new ThreadSleepRetrySleeper(),
                new SystemRetryClock(),
                IdGenerators.uuidV7()
        );
    }

    public DefaultRetryExecutor(
            RetryPolicyRegistry retryPolicyRegistry,
            List<RetryListener> retryListeners,
            RetrySleeper retrySleeper) {
        this(
                retryPolicyRegistry,
                retryListeners,
                retrySleeper,
                new SystemRetryClock(),
                IdGenerators.uuidV7()
        );
    }

    public DefaultRetryExecutor(
            RetryPolicyRegistry retryPolicyRegistry,
            List<RetryListener> retryListeners,
            RetrySleeper retrySleeper,
            RetryClock retryClock,
            StringIdGenerator retryIdGenerator) {
        this.retryPolicyRegistry = Arguments.notNull(
                retryPolicyRegistry,
                "retryPolicyRegistry"
        );
        this.retrySleeper = Arguments.notNull(retrySleeper, "retrySleeper");
        this.retryClock = Arguments.notNull(retryClock, "retryClock");
        this.eventDispatcher = new RetryEventDispatcher(retryListeners, this.retryClock);
        this.retryIdGenerator = Arguments.notNull(
                retryIdGenerator,
                "retryIdGenerator"
        );
    }

    /** 解析命名策略并执行带属性的业务操作。 */
    @Override
    public <T> RetryResult<T> execute(
            String operationName,
            Map<String, Object> attributes,
            RetryOperation<T> operation,
            String policyName) {
        RetryPolicy retryPolicy = retryPolicyRegistry.getRequired(policyName);
        RetryExecution<T> execution = RetryExecution
                .builder(operationName, operation, retryPolicy)
                .attributes(attributes)
                .build();
        return execute(execution);
    }

    /** 执行一个已经完整构建的同步重试请求。 */
    @Override
    public <T> RetryResult<T> execute(RetryExecution<T> execution) {
        RetryExecution<T> actualExecution = Arguments.notNull(
                execution,
                "execution"
        );
        RetryPolicy retryPolicy = actualExecution.getRetryPolicy();
        String operationName = actualExecution.getOperationName();
        String retryId = resolveRetryId(actualExecution);
        Instant executionStartTime = retryClock.now();
        long executionStartNanos = retryClock.nanoTime();
        RetryAttempt<T> previousAttempt = null;

        // 先发布逻辑执行开始事件，后续所有尝试事件都使用同一个 retryId 进行关联。
        eventDispatcher.publish(eventDispatcher.newEvent(
                RetryEventType.EXECUTION_STARTED,
                retryId,
                operationName,
                retryPolicy,
                Duration.ZERO
        ).build());

        // 非幂等操作允许多次尝试时只做风险告警，不替代业务幂等保护。
        if (retryPolicy.shouldWarnUnsafeRetry()) {
            eventDispatcher.publish(eventDispatcher.newEvent(
                    RetryEventType.SAFETY_WARNING,
                    retryId,
                    operationName,
                    retryPolicy,
                    Duration.ZERO
            ).notice(
                    "NON_IDEMPOTENT operation is configured with multiple attempts",
                    "UNSAFE_RETRY_CONFIGURATION",
                    RetryFailureCategory.NON_RETRYABLE
            ).build());
        }

        // maxAttempts 包含第一次正常执行，因此循环编号从 1 开始。
        for (int attemptNumber = 1;
             attemptNumber <= retryPolicy.getMaxAttempts();
             attemptNumber++) {
            Duration beforeAttemptElapsed = elapsedSince(executionStartNanos);

            // 每次物理尝试开始前统一检查中断、取消和总时长预算。
            RetryResult<T> preAttemptTerminal = checkBeforeAttempt(
                    actualExecution,
                    retryId,
                    previousAttempt,
                    attemptNumber,
                    beforeAttemptElapsed
            );
            if (preAttemptTerminal != null) {
                return preAttemptTerminal;
            }

            RetryContext retryContext = createContext(
                    actualExecution,
                    retryId,
                    executionStartTime,
                    attemptNumber,
                    beforeAttemptElapsed,
                    previousAttempt
            );

            eventDispatcher.publish(eventDispatcher.newEvent(
                    RetryEventType.ATTEMPT_STARTED,
                    retryId,
                    operationName,
                    retryPolicy,
                    beforeAttemptElapsed
            ).attemptNumber(attemptNumber).build());

            // 业务异常被封装为 RetryAttempt，Error 则继续向上传播。
            RetryAttempt<T> attempt = executeAttempt(
                    actualExecution,
                    retryContext,
                    retryId,
                    executionStartTime,
                    executionStartNanos
            );

            eventDispatcher.publish(eventDispatcher.newEvent(
                    RetryEventType.ATTEMPT_COMPLETED,
                    retryId,
                    operationName,
                    retryPolicy,
                    attempt.getTotalElapsedTime()
            ).attemptNumber(attemptNumber)
                    .attemptDuration(attempt.getAttemptDuration())
                    .failure(attempt.getFailure())
                    .build());

            // InterruptedException 必须立即终止，并在 terminal 方法中恢复线程中断标记。
            if (attempt.getFailure() instanceof InterruptedException interruptedException) {
                return terminal(
                        retryId,
                        operationName,
                        retryPolicy,
                        RetryStatus.INTERRUPTED,
                        attempt,
                        attempt.getResult(),
                        interruptedException,
                        RetryDecision.abort("operation was interrupted", "INTERRUPTED"),
                        RetryEventType.EXECUTION_INTERRUPTED,
                        attempt.getTotalElapsedTime()
                );
            }

            // 分类器只负责解释本次结果，不负责次数和时间边界判断。
            RetryDecision decision;
            try {
                decision = classifyAttempt(retryPolicy, attempt);
            } catch (RuntimeException classifierFailure) {
                return terminal(
                        retryId,
                        operationName,
                        retryPolicy,
                        RetryStatus.EXECUTION_FAILED,
                        attempt,
                        attempt.getResult(),
                        classifierFailure,
                        RetryDecision.abort(
                                "retry classifier failed or violated its contract",
                                "CLASSIFIER_FAILED"
                        ),
                        RetryEventType.EXECUTION_FAILED,
                        attempt.getTotalElapsedTime()
                );
            }

            eventDispatcher.publish(eventDispatcher.newEvent(
                    RetryEventType.DECISION_MADE,
                    retryId,
                    operationName,
                    retryPolicy,
                    attempt.getTotalElapsedTime()
            ).attemptNumber(attemptNumber)
                    .attemptDuration(attempt.getAttemptDuration())
                    .decision(decision)
                    .failure(attempt.getFailure())
                    .build());

            // SUCCESS、STOP 和 ABORT 在这里收敛为最终结果，只有 RETRY 才继续后续流程。
            RetryResult<T> decisionTerminal = handleTerminalDecision(
                    retryId,
                    operationName,
                    retryPolicy,
                    attempt,
                    decision
            );
            if (decisionTerminal != null) {
                return decisionTerminal;
            }

            // 在计算退避前先检查最大次数和取消状态，避免无意义地调用退避策略。
            RetryResult<T> retryBoundaryTerminal = checkRetryBoundaries(
                    actualExecution,
                    retryId,
                    attempt,
                    decision
            );
            if (retryBoundaryTerminal != null) {
                return retryBoundaryTerminal;
            }

            // 决策中的协议指定延迟优先于通用退避策略，例如 HTTP Retry-After。
            RetryDelay retryDelay;
            try {
                retryDelay = resolveDelay(
                        retryPolicy.getBackoffStrategy(),
                        attempt,
                        decision
                );
            } catch (RuntimeException backoffFailure) {
                return terminal(
                        retryId,
                        operationName,
                        retryPolicy,
                        RetryStatus.EXECUTION_FAILED,
                        attempt,
                        attempt.getResult(),
                        backoffFailure,
                        RetryDecision.abort(
                                "backoff strategy failed or violated its contract",
                                "BACKOFF_FAILED"
                        ),
                        RetryEventType.EXECUTION_FAILED,
                        attempt.getTotalElapsedTime()
                );
            }

            // 退避时间达到或超过剩余预算时不再等待，直接按总超时结束。
            Duration remainingDuration = remaining(
                    retryPolicy.getMaxDuration(),
                    attempt.getTotalElapsedTime()
            );
            if (!retryDelay.isZero()
                    && retryDelay.getDuration().compareTo(remainingDuration) >= 0) {
                return terminal(
                        retryId,
                        operationName,
                        retryPolicy,
                        RetryStatus.TIMED_OUT,
                        attempt,
                        attempt.getResult(),
                        attempt.getFailure(),
                        decision,
                        RetryEventType.EXECUTION_TIMED_OUT,
                        attempt.getTotalElapsedTime()
                );
            }

            eventDispatcher.publish(eventDispatcher.newEvent(
                    RetryEventType.RETRY_SCHEDULED,
                    retryId,
                    operationName,
                    retryPolicy,
                    attempt.getTotalElapsedTime()
            ).attemptNumber(attemptNumber)
                    .attemptDuration(attempt.getAttemptDuration())
                    .decision(decision)
                    .retryDelay(retryDelay)
                    .failure(attempt.getFailure())
                    .build());

            // 同步等待期间仍要正确响应线程中断，等待结束后再次检查取消状态。
            RetryResult<T> waitTerminal = waitBeforeRetry(
                    actualExecution,
                    retryId,
                    attempt,
                    decision,
                    retryDelay,
                    executionStartNanos
            );
            if (waitTerminal != null) {
                return waitTerminal;
            }

            previousAttempt = attempt;
        }

        throw new IllegalStateException("Retry execution reached an unreachable branch");
    }

    /** 解析调用方指定或自动生成的逻辑执行标识。 */
    private <T> String resolveRetryId(RetryExecution<T> execution) {
        if (execution.getRetryId() != null) {
            return execution.getRetryId();
        }
        String generated = retryIdGenerator.nextId();
        return Arguments.notBlank(generated, "generated retryId").trim();
    }

    /** 在开始一次物理尝试前统一处理中断、取消和总时长预算。 */
    private <T> RetryResult<T> checkBeforeAttempt(
            RetryExecution<T> execution,
            String retryId,
            RetryAttempt<T> previousAttempt,
            int attemptNumber,
            Duration elapsedTime) {
        RetryPolicy retryPolicy = execution.getRetryPolicy();
        if (Thread.currentThread().isInterrupted()) {
            InterruptedException interruptedException = new InterruptedException(
                    "Thread was interrupted before retry attempt started"
            );
            return terminal(
                    retryId,
                    execution.getOperationName(),
                    retryPolicy,
                    RetryStatus.INTERRUPTED,
                    previousAttempt,
                    previousAttempt == null ? null : previousAttempt.getResult(),
                    interruptedException,
                    RetryDecision.abort(
                            "thread was interrupted before the attempt",
                            "INTERRUPTED"
                    ),
                    RetryEventType.EXECUTION_INTERRUPTED,
                    elapsedTime
            );
        }

        RetryResult<T> cancellationFailure = checkCancellation(
                execution,
                retryId,
                previousAttempt,
                elapsedTime,
                "cancellation was requested before attempt " + attemptNumber
        );
        if (cancellationFailure != null) {
            return cancellationFailure;
        }

        if (elapsedTime.compareTo(retryPolicy.getMaxDuration()) >= 0) {
            RetryDecision decision = RetryDecision.stop(
                    "maximum duration was reached before the next attempt",
                    "MAX_DURATION_REACHED",
                    RetryFailureCategory.NON_RETRYABLE
            );
            return terminal(
                    retryId,
                    execution.getOperationName(),
                    retryPolicy,
                    RetryStatus.TIMED_OUT,
                    previousAttempt,
                    previousAttempt == null ? null : previousAttempt.getResult(),
                    previousAttempt == null ? null : previousAttempt.getFailure(),
                    decision,
                    RetryEventType.EXECUTION_TIMED_OUT,
                    elapsedTime
            );
        }
        return null;
    }

    /** 创建传递给业务操作的当前尝试上下文。 */
    private <T> RetryContext createContext(
            RetryExecution<T> execution,
            String retryId,
            Instant executionStartTime,
            int attemptNumber,
            Duration elapsedTime,
            RetryAttempt<T> previousAttempt) {
        return new RetryContext(
                retryId,
                execution.getOperationName(),
                execution.getRetryPolicy().getPolicyName(),
                attemptNumber,
                executionStartTime,
                elapsedTime,
                remaining(execution.getRetryPolicy().getMaxDuration(), elapsedTime),
                previousAttempt,
                execution.getAttributes(),
                execution.getCancellationToken()
        );
    }

    /** 调用业务操作并形成完整尝试快照。 */
    private <T> RetryAttempt<T> executeAttempt(
            RetryExecution<T> execution,
            RetryContext retryContext,
            String retryId,
            Instant executionStartTime,
            long executionStartNanos) {
        Instant attemptStartTime = retryClock.clock().instant();
        long attemptStartNanos = retryClock.nanoTime();
        T result = null;
        Throwable failure = null;
        try {
            result = execution.getOperation().execute(retryContext);
        } catch (InterruptedException interruptedException) {
            failure = IronExceptions.restoreInterrupt(interruptedException);
        } catch (Exception exception) {
            failure = exception;
        }
        Duration attemptDuration = elapsedSince(attemptStartNanos);
        Duration totalElapsedTime = elapsedSince(executionStartNanos);
        return new RetryAttempt<>(
                retryId,
                execution.getOperationName(),
                execution.getRetryPolicy().getPolicyName(),
                retryContext.getAttemptNumber(),
                executionStartTime,
                attemptStartTime,
                retryClock.clock().instant(),
                attemptDuration,
                totalElapsedTime,
                remaining(execution.getRetryPolicy().getMaxDuration(), totalElapsedTime),
                result,
                failure,
                execution.getAttributes()
        );
    }

    /** 调用分类器并验证决策与尝试结果保持一致。 */
    private static RetryDecision classifyAttempt(
            RetryPolicy retryPolicy,
            RetryAttempt<?> attempt) {
        RetryDecision decision = Objects.requireNonNull(
                retryPolicy.getRetryClassifier().classify(attempt),
                "RetryClassifier returned null decision"
        );
        if (!isDecisionConsistent(attempt, decision)) {
            throw new IllegalStateException(
                    "RetryClassifier cannot classify a failed attempt as SUCCESS"
            );
        }
        return decision;
    }

    private static boolean isDecisionConsistent(
            RetryAttempt<?> attempt,
            RetryDecision decision) {
        if (decision == null) {
            return false;
        }
        return !attempt.hasFailure() || decision.getType() != RetryDecisionType.SUCCESS;
    }

    /** 将 SUCCESS、STOP 和 ABORT 决策转换为最终结果。 */
    private <T> RetryResult<T> handleTerminalDecision(
            String retryId,
            String operationName,
            RetryPolicy retryPolicy,
            RetryAttempt<T> attempt,
            RetryDecision decision) {
        return switch (decision.getType()) {
            case SUCCESS -> terminal(
                    retryId,
                    operationName,
                    retryPolicy,
                    RetryStatus.SUCCESS,
                    attempt,
                    attempt.getResult(),
                    null,
                    decision,
                    RetryEventType.EXECUTION_SUCCEEDED,
                    attempt.getTotalElapsedTime()
            );
            case STOP -> terminal(
                    retryId,
                    operationName,
                    retryPolicy,
                    RetryStatus.NOT_RETRYABLE,
                    attempt,
                    attempt.getResult(),
                    attempt.getFailure(),
                    decision,
                    RetryEventType.EXECUTION_NOT_RETRYABLE,
                    attempt.getTotalElapsedTime()
            );
            case ABORT -> terminal(
                    retryId,
                    operationName,
                    retryPolicy,
                    RetryStatus.ABORTED,
                    attempt,
                    attempt.getResult(),
                    attempt.getFailure(),
                    decision,
                    RetryEventType.EXECUTION_ABORTED,
                    attempt.getTotalElapsedTime()
            );
            case RETRY -> null;
        };
    }

    /** 在真正等待前检查次数、总时长和协作式取消边界。 */
    private <T> RetryResult<T> checkRetryBoundaries(
            RetryExecution<T> execution,
            String retryId,
            RetryAttempt<T> attempt,
            RetryDecision decision) {
        RetryPolicy retryPolicy = execution.getRetryPolicy();
        if (attempt.getAttemptNumber() >= retryPolicy.getMaxAttempts()) {
            return terminal(
                    retryId,
                    execution.getOperationName(),
                    retryPolicy,
                    RetryStatus.EXHAUSTED,
                    attempt,
                    attempt.getResult(),
                    attempt.getFailure(),
                    decision,
                    RetryEventType.EXECUTION_EXHAUSTED,
                    attempt.getTotalElapsedTime()
            );
        }
        if (attempt.getTotalElapsedTime().compareTo(retryPolicy.getMaxDuration()) >= 0) {
            return terminal(
                    retryId,
                    execution.getOperationName(),
                    retryPolicy,
                    RetryStatus.TIMED_OUT,
                    attempt,
                    attempt.getResult(),
                    attempt.getFailure(),
                    decision,
                    RetryEventType.EXECUTION_TIMED_OUT,
                    attempt.getTotalElapsedTime()
            );
        }
        return checkCancellation(
                execution,
                retryId,
                attempt,
                attempt.getTotalElapsedTime(),
                "cancellation was requested before scheduling the next retry"
        );
    }

    /** 选择决策覆盖等待时间或调用策略默认退避。 */
    private static RetryDelay resolveDelay(
            BackoffStrategy backoffStrategy,
            RetryAttempt<?> attempt,
            RetryDecision decision) {
        if (decision.getDelayOverride().isPresent()) {
            return RetryDelay.of(
                    decision.getDelayOverride().orElseThrow(),
                    decision.getDelaySource(),
                    "delay override from retry decision: " + decision.getReason()
            );
        }
        return Objects.requireNonNull(
                backoffStrategy.nextDelay(attempt, decision),
                "BackoffStrategy returned null delay"
        );
    }

    /** 执行同步退避等待并处理中断、取消和等待器失败。 */
    private <T> RetryResult<T> waitBeforeRetry(
            RetryExecution<T> execution,
            String retryId,
            RetryAttempt<T> attempt,
            RetryDecision decision,
            RetryDelay retryDelay,
            long executionStartNanos) {
        RetryResult<T> beforeWaitCancellation = checkCancellation(
                execution,
                retryId,
                attempt,
                attempt.getTotalElapsedTime(),
                "cancellation was requested before backoff wait"
        );
        if (beforeWaitCancellation != null) {
            return beforeWaitCancellation;
        }
        try {
            retrySleeper.sleep(retryDelay.getDuration());
        } catch (InterruptedException interruptedException) {
            IronExceptions.restoreInterrupt(interruptedException);
            return terminal(
                    retryId,
                    execution.getOperationName(),
                    execution.getRetryPolicy(),
                    RetryStatus.INTERRUPTED,
                    attempt,
                    attempt.getResult(),
                    interruptedException,
                    RetryDecision.abort(
                            "backoff wait was interrupted",
                            "INTERRUPTED"
                    ),
                    RetryEventType.EXECUTION_INTERRUPTED,
                    elapsedSince(executionStartNanos)
            );
        } catch (RuntimeException sleeperFailure) {
            return terminal(
                    retryId,
                    execution.getOperationName(),
                    execution.getRetryPolicy(),
                    RetryStatus.EXECUTION_FAILED,
                    attempt,
                    attempt.getResult(),
                    sleeperFailure,
                    RetryDecision.abort("retry sleeper failed", "SLEEPER_FAILED"),
                    RetryEventType.EXECUTION_FAILED,
                    elapsedSince(executionStartNanos)
            );
        }
        return checkCancellation(
                execution,
                retryId,
                attempt,
                elapsedSince(executionStartNanos),
                "cancellation was requested after backoff wait"
        );
    }

    /** 查询取消令牌并将取消或令牌异常转换为统一结果。 */
    private <T> RetryResult<T> checkCancellation(
            RetryExecution<T> execution,
            String retryId,
            RetryAttempt<T> lastAttempt,
            Duration elapsedTime,
            String reason) {
        RetryCancellationToken cancellationToken = execution.getCancellationToken();
        final boolean cancellationRequested;
        try {
            cancellationRequested = cancellationToken.isCancellationRequested();
        } catch (RuntimeException cancellationFailure) {
            return terminal(
                    retryId,
                    execution.getOperationName(),
                    execution.getRetryPolicy(),
                    RetryStatus.EXECUTION_FAILED,
                    lastAttempt,
                    lastAttempt == null ? null : lastAttempt.getResult(),
                    cancellationFailure,
                    RetryDecision.abort(
                            "cancellation token failed",
                            "CANCELLATION_TOKEN_FAILED"
                    ),
                    RetryEventType.EXECUTION_FAILED,
                    elapsedTime
            );
        }
        if (!cancellationRequested) {
            return null;
        }
        return terminal(
                retryId,
                execution.getOperationName(),
                execution.getRetryPolicy(),
                RetryStatus.CANCELLED,
                lastAttempt,
                lastAttempt == null ? null : lastAttempt.getResult(),
                lastAttempt == null ? null : lastAttempt.getFailure(),
                RetryDecision.abort(reason, "CANCELLED"),
                RetryEventType.EXECUTION_CANCELLED,
                elapsedTime
        );
    }

    /** 创建统一最终结果并发布对应终态事件。 */
    private <T> RetryResult<T> terminal(
            String retryId,
            String operationName,
            RetryPolicy retryPolicy,
            RetryStatus status,
            RetryAttempt<T> lastAttempt,
            T value,
            Throwable failure,
            RetryDecision decision,
            RetryEventType eventType,
            Duration elapsedTime) {
        int attempts = lastAttempt == null ? 0 : lastAttempt.getAttemptNumber();
        RetryResult<T> result = RetryResult.of(
                retryId,
                operationName,
                retryPolicy.getPolicyName(),
                status,
                value,
                failure,
                attempts,
                elapsedTime,
                lastAttempt,
                decision
        );
        RetryEvent.Builder eventBuilder = eventDispatcher.newEvent(
                eventType,
                retryId,
                operationName,
                retryPolicy,
                elapsedTime
        ).attemptNumber(attempts)
                .decision(decision)
                .finalStatus(status)
                .failure(failure);
        if (lastAttempt != null) {
            eventBuilder.attemptDuration(lastAttempt.getAttemptDuration());
        }
        eventDispatcher.publish(eventBuilder.build());
        return result;
    }

    /** 使用单调时钟计算非负耗时。 */
    private Duration elapsedSince(long startNanos) {
        long delta = retryClock.nanoTime() - startNanos;
        return Duration.ofNanos(Math.max(0L, delta));
    }

    /** 计算非负剩余时长预算。 */
    private static Duration remaining(Duration maxDuration, Duration elapsedTime) {
        Duration remainingDuration = maxDuration.minus(elapsedTime);
        return remainingDuration.isNegative() ? Duration.ZERO : remainingDuration;
    }

}
