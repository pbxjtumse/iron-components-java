package com.xjtu.iron.reliable.task.core.client;

import com.xjtu.iron.reliable.task.api.client.*;
import com.xjtu.iron.reliable.task.api.model.ReliableTask;
import com.xjtu.iron.reliable.task.api.model.ReliableTaskKey;
import com.xjtu.iron.reliable.task.api.model.ReliableTaskStatus;
import com.xjtu.iron.reliable.task.api.repository.ReliableTaskAdminTransitionCommand;
import com.xjtu.iron.reliable.task.api.repository.ReliableTaskRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** version CAS 保护的最小人工操作实现。 */
public final class DefaultReliableTaskAdminClient implements ReliableTaskAdminClient {

    private static final Set<ReliableTaskStatus> REQUEUEABLE =
            EnumSet.of(ReliableTaskStatus.MANUAL, ReliableTaskStatus.DEAD);
    private static final Set<ReliableTaskStatus> CANCELLABLE = EnumSet.of(
            ReliableTaskStatus.READY,
            ReliableTaskStatus.RETRY_WAIT,
            ReliableTaskStatus.WAIT_RECONCILE,
            ReliableTaskStatus.MANUAL
    );

    private final ReliableTaskRepository repository;
    private final Clock clock;

    public DefaultReliableTaskAdminClient(ReliableTaskRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public ReliableTaskAdminResult requeue(ReliableTaskKey key, long expectedVersion, Instant executeAt) {
        Objects.requireNonNull(executeAt, "executeAt must not be null");
        return transition(key, expectedVersion, REQUEUEABLE, ReliableTaskStatus.READY, executeAt, true);
    }

    @Override
    public ReliableTaskAdminResult cancel(ReliableTaskKey key, long expectedVersion) {
        return transition(key, expectedVersion, CANCELLABLE, ReliableTaskStatus.CANCELLED, null, false);
    }

    private ReliableTaskAdminResult transition(
            ReliableTaskKey key,
            long expectedVersion,
            Set<ReliableTaskStatus> allowed,
            ReliableTaskStatus target,
            Instant executeAt,
            boolean resetAttempts) {
        ReliableTask current = repository.find(Objects.requireNonNull(key, "key must not be null")).orElse(null);
        if (current == null) {
            return new ReliableTaskAdminResult(ReliableTaskAdminStatus.NOT_FOUND, null);
        }
        if (!allowed.contains(current.getStatus())) {
            return new ReliableTaskAdminResult(ReliableTaskAdminStatus.INVALID_STATE, current);
        }
        if (current.getVersion() != expectedVersion) {
            return new ReliableTaskAdminResult(ReliableTaskAdminStatus.STALE_VERSION, current);
        }

        boolean updated = repository.adminTransition(new ReliableTaskAdminTransitionCommand(
                key,
                current.getStatus(),
                expectedVersion,
                target,
                executeAt,
                resetAttempts,
                clock.instant()
        ));
        ReliableTask latest = repository.find(key).orElse(current);
        return new ReliableTaskAdminResult(
                updated ? ReliableTaskAdminStatus.UPDATED : ReliableTaskAdminStatus.STALE_VERSION,
                latest
        );
    }
}
