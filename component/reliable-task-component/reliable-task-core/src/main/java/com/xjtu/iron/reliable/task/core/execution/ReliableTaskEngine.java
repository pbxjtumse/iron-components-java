package com.xjtu.iron.reliable.task.core.execution;

import com.xjtu.iron.reliable.task.api.client.ReliableTaskRunResult;
import com.xjtu.iron.reliable.task.api.client.ReliableTaskRunStatus;
import com.xjtu.iron.reliable.task.api.execution.ReliableTaskExecutionContext;
import com.xjtu.iron.reliable.task.api.execution.ReliableTaskExecutionOutcome;
import com.xjtu.iron.reliable.task.api.execution.ReliableTaskExecutionResult;
import com.xjtu.iron.reliable.task.api.execution.ReliableTaskHandler;
import com.xjtu.iron.reliable.task.api.model.ReliableTask;
import com.xjtu.iron.reliable.task.api.model.ReliableTaskStatus;
import com.xjtu.iron.reliable.task.api.repository.*;
import com.xjtu.iron.reliable.task.core.config.ReliableTaskRuntimePolicy;
import com.xjtu.iron.reliable.task.core.handler.ReliableTaskHandlerRegistry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** 所有执行入口共享的 claim -> handler -> transition 模板。 */
public final class ReliableTaskEngine {

    private static final String MISSING_HANDLER = "MISSING_HANDLER";
    private static final String UNHANDLED_EXCEPTION = "UNHANDLED_EXCEPTION";
    private static final String MAX_ATTEMPTS = "MAX_ATTEMPTS";

    private final ReliableTaskRepository repository;
    private final ReliableTaskHandlerRegistry handlerRegistry;
    private final ReliableTaskRuntimePolicy policy;
    private final Clock clock;
    private final String ownerId;

    public ReliableTaskEngine(
            ReliableTaskRepository repository,
            ReliableTaskHandlerRegistry handlerRegistry,
            ReliableTaskRuntimePolicy policy,
            Clock clock,
            String ownerId) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.handlerRegistry = Objects.requireNonNull(handlerRegistry, "handlerRegistry must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
        this.ownerId = ownerId.trim();
    }

    /** Candidate 只是快照；本方法首先做第二次 CAS，成功后才调用 Handler。 */
    public ReliableTaskRunResult runCandidate(ReliableTask candidate) {
        Objects.requireNonNull(candidate, "candidate must not be null");
        Instant claimTime = clock.instant();
        ReliableTaskClaimResult claim = repository.tryClaim(new ReliableTaskClaimCommand(
                candidate,
                ownerId,
                claimTime,
                claimTime.plus(policy.getLeaseDuration())
        ));
        if (!claim.claimed()) {
            return new ReliableTaskRunResult(ReliableTaskRunStatus.NOT_CLAIMED, null, null);
        }

        ReliableTask task = claim.task();
        // 允许扫描器抢占“Lease 已过期且次数已经耗尽”的遗留 RUNNING 记录，
        // 但该次抢占只负责收口为 DEAD，不再调用业务 Handler。
        ReliableTaskExecutionResult executionResult = candidate.getAttemptCount() >= candidate.getMaxAttempts()
                ? ReliableTaskExecutionResult.dead(
                        MAX_ATTEMPTS,
                        "maximum durable execution attempts reached before claim"
                )
                : executeHandler(task, claim.claimedFromStatus());
        ReliableTaskTransitionCommand transition = transitionOf(task, executionResult, clock.instant());
        boolean updated = repository.transition(transition);
        ReliableTask finalTask = repository.find(task.getKey()).orElse(null);
        return new ReliableTaskRunResult(
                updated ? ReliableTaskRunStatus.EXECUTED : ReliableTaskRunStatus.STALE_COMPLETION,
                task,
                finalTask
        );
    }

    private ReliableTaskExecutionResult executeHandler(
            ReliableTask task,
            ReliableTaskStatus claimedFromStatus) {
        ReliableTaskHandler handler = handlerRegistry.find(task.getTaskType()).orElse(null);
        if (handler == null) {
            return ReliableTaskExecutionResult.manual(
                    MISSING_HANDLER,
                    "no ReliableTaskHandler registered for taskType=" + task.getTaskType()
            );
        }

        AtomicReference<Instant> trackedLeaseUntil = new AtomicReference<>(task.getLeaseUntil());
        ReliableTaskExecutionContext context = new ReliableTaskExecutionContext(
                task,
                claimedFromStatus,
                extension -> renewLease(task, extension, trackedLeaseUntil)
        );
        try {
            ReliableTaskExecutionResult result = handler.execute(context);
            return result == null
                    ? ReliableTaskExecutionResult.dead("NULL_HANDLER_RESULT", "handler returned null")
                    : result;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return ReliableTaskExecutionResult.retryAfter(
                    policy.getFailureRetryDelay(),
                    "INTERRUPTED",
                    safeMessage(interrupted)
            );
        } catch (Exception failure) {
            return ReliableTaskExecutionResult.retryAfter(
                    policy.getFailureRetryDelay(),
                    UNHANDLED_EXCEPTION,
                    safeMessage(failure)
            );
        }
    }

    private boolean renewLease(
            ReliableTask task,
            Duration extension,
            AtomicReference<Instant> trackedLeaseUntil) {
        Instant now = clock.instant();
        Instant currentLeaseUntil = trackedLeaseUntil.get();
        if (currentLeaseUntil == null || !currentLeaseUntil.isAfter(now)) {
            return false;
        }
        Instant newLeaseUntil = currentLeaseUntil.plus(extension);
        boolean renewed = repository.renewLease(new ReliableTaskLeaseRenewCommand(
                task.getKey(),
                task.getOwnerId(),
                task.getVersion(),
                now,
                newLeaseUntil
        ));
        if (renewed) {
            trackedLeaseUntil.set(newLeaseUntil);
        }
        return renewed;
    }

    private ReliableTaskTransitionCommand transitionOf(
            ReliableTask task,
            ReliableTaskExecutionResult result,
            Instant now) {
        ReliableTaskExecutionOutcome outcome = result.getOutcome();
        ReliableTaskStatus target;
        Instant next = null;
        String code = result.getCode();
        String message = result.getMessage();

        if ((outcome == ReliableTaskExecutionOutcome.RETRY
                || outcome == ReliableTaskExecutionOutcome.RECONCILE)
                && task.getAttemptCount() >= task.getMaxAttempts()) {
            target = ReliableTaskStatus.DEAD;
            code = MAX_ATTEMPTS;
            message = "maximum durable execution attempts reached; last=" + safeText(result.getMessage());
        } else {
            switch (outcome) {
                case SUCCESS -> target = ReliableTaskStatus.SUCCEEDED;
                case RETRY -> {
                    target = ReliableTaskStatus.RETRY_WAIT;
                    next = now.plus(result.getDelay());
                }
                case RECONCILE -> {
                    target = ReliableTaskStatus.WAIT_RECONCILE;
                    next = now.plus(result.getDelay());
                }
                case MANUAL -> target = ReliableTaskStatus.MANUAL;
                case DEAD -> target = ReliableTaskStatus.DEAD;
                default -> throw new IllegalStateException("unsupported outcome: " + outcome);
            }
        }

        return new ReliableTaskTransitionCommand(
                task.getKey(),
                task.getOwnerId(),
                task.getVersion(),
                target,
                next,
                code,
                message,
                now
        );
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return failure.getClass().getName() + (message == null ? "" : ": " + message);
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }
}
