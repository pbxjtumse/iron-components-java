package com.xjtu.iron.reliable.task.api.execution;

import java.time.Duration;
import java.util.Objects;

/** Handler 返回给 Reliable Task 状态机的显式结果。 */
public final class ReliableTaskExecutionResult {

    private final ReliableTaskExecutionOutcome outcome;
    private final Duration delay;
    private final String code;
    private final String message;

    private ReliableTaskExecutionResult(
            ReliableTaskExecutionOutcome outcome,
            Duration delay,
            String code,
            String message) {
        this.outcome = Objects.requireNonNull(outcome, "outcome must not be null");
        if (delay != null && delay.isNegative()) {
            throw new IllegalArgumentException("delay must not be negative");
        }
        this.delay = delay;
        this.code = normalize(code);
        this.message = normalize(message);
        if ((outcome == ReliableTaskExecutionOutcome.RETRY || outcome == ReliableTaskExecutionOutcome.RECONCILE)
                && delay == null) {
            throw new IllegalArgumentException(outcome + " requires delay");
        }
    }

    public static ReliableTaskExecutionResult success() {
        return new ReliableTaskExecutionResult(ReliableTaskExecutionOutcome.SUCCESS, null, null, null);
    }

    public static ReliableTaskExecutionResult retryAfter(Duration delay, String code, String message) {
        return new ReliableTaskExecutionResult(ReliableTaskExecutionOutcome.RETRY, delay, code, message);
    }

    public static ReliableTaskExecutionResult reconcileAfter(Duration delay, String code, String message) {
        return new ReliableTaskExecutionResult(ReliableTaskExecutionOutcome.RECONCILE, delay, code, message);
    }

    public static ReliableTaskExecutionResult manual(String code, String message) {
        return new ReliableTaskExecutionResult(ReliableTaskExecutionOutcome.MANUAL, null, code, message);
    }

    public static ReliableTaskExecutionResult dead(String code, String message) {
        return new ReliableTaskExecutionResult(ReliableTaskExecutionOutcome.DEAD, null, code, message);
    }

    public ReliableTaskExecutionOutcome getOutcome() { return outcome; }
    public Duration getDelay() { return delay; }
    public String getCode() { return code; }
    public String getMessage() { return message; }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
