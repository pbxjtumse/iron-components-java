package com.xjtu.iron.reliable.task.core;

import com.xjtu.iron.foundation.id.api.StringIdGenerator;
import com.xjtu.iron.reliable.task.api.client.ReliableTaskClient;
import com.xjtu.iron.reliable.task.api.execution.ReliableTaskExecutionContext;
import com.xjtu.iron.reliable.task.api.execution.ReliableTaskExecutionResult;
import com.xjtu.iron.reliable.task.api.execution.ReliableTaskHandler;
import com.xjtu.iron.reliable.task.api.model.*;
import com.xjtu.iron.reliable.task.api.operation.admin.*;
import com.xjtu.iron.reliable.task.api.operation.run.*;
import com.xjtu.iron.reliable.task.api.operation.submit.*;
import com.xjtu.iron.reliable.task.api.repository.ReliableTaskRepository;
import com.xjtu.iron.reliable.task.api.repository.claim.*;
import com.xjtu.iron.reliable.task.api.repository.create.ReliableTaskCreateResult;
import com.xjtu.iron.reliable.task.api.repository.lease.ReliableTaskLeaseRenewCommand;
import com.xjtu.iron.reliable.task.api.repository.scan.ReliableTaskScanQuery;
import com.xjtu.iron.reliable.task.api.repository.transition.*;
import com.xjtu.iron.reliable.task.api.scan.*;
import com.xjtu.iron.reliable.task.api.state.ReliableTaskStatus;
import com.xjtu.iron.reliable.task.core.client.DefaultReliableTaskClient;
import com.xjtu.iron.reliable.task.core.client.DefaultReliableTaskAdminClient;
import com.xjtu.iron.reliable.task.core.policy.ReliableTaskRuntimePolicy;
import com.xjtu.iron.reliable.task.core.execution.ReliableTaskEngine;
import com.xjtu.iron.reliable.task.core.execution.handler.DefaultReliableTaskHandlerRegistry;
import com.xjtu.iron.reliable.task.core.scan.DefaultReliableTaskScanner;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ReliableTaskCoreFlowTest {

    @Test
    void shouldUseSameEngineForFastPathAndScanFallback() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-09T00:00:00Z"));
        InMemoryRepository repository = new InMemoryRepository();
        AtomicInteger calls = new AtomicInteger();
        ReliableTaskHandler handler = new ReliableTaskHandler() {
            @Override
            public String taskType() { return "demo"; }

            @Override
            public ReliableTaskExecutionResult execute(com.xjtu.iron.reliable.task.api.execution.ReliableTaskExecutionContext context) {
                return calls.incrementAndGet() == 1
                        ? ReliableTaskExecutionResult.retryAfter(Duration.ofSeconds(30), "TEMPORARY", "try later")
                        : ReliableTaskExecutionResult.success();
            }
        };
        ReliableTaskRuntimePolicy policy = new ReliableTaskRuntimePolicy(3, 8, Duration.ofSeconds(10), Duration.ofSeconds(30));
        ReliableTaskEngine engine = new ReliableTaskEngine(
                repository,
                new DefaultReliableTaskHandlerRegistry(List.of(handler)),
                policy,
                clock,
                "owner-a"
        );
        StringIdGenerator ids = () -> "generated-id";
        ReliableTaskClient client = new DefaultReliableTaskClient(repository, engine, policy, ids, clock);
        ReliableTaskScanner scanner = new DefaultReliableTaskScanner(repository, engine, clock);

        ReliableTaskSubmitResult submitted = client.submit(
                ReliableTaskSubmission.builder("demo", "{}").taskId("task-1").scanBucket(2).build()
        );
        ReliableTaskRunResult first = client.runNow(submitted.task().getKey());

        assertThat(first.status()).isEqualTo(ReliableTaskRunStatus.EXECUTED);
        assertThat(first.finalTask().getStatus()).isEqualTo(ReliableTaskStatus.RETRY_WAIT);
        assertThat(first.finalTask().getAttemptCount()).isEqualTo(1);

        clock.advance(Duration.ofSeconds(31));
        ReliableTaskScanReport report = scanner.scan(new ReliableTaskScanRequest("default", 2, 10));

        assertThat(report.executed()).isEqualTo(1);
        assertThat(client.find(submitted.task().getKey()).orElseThrow().getStatus())
                .isEqualTo(ReliableTaskStatus.SUCCEEDED);
        assertThat(calls).hasValue(2);
    }

    @Test
    void shouldMoveMissingHandlerToManualInsteadOfRetryStorm() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-09T00:00:00Z"));
        InMemoryRepository repository = new InMemoryRepository();
        ReliableTaskRuntimePolicy policy = ReliableTaskRuntimePolicy.defaults();
        ReliableTaskEngine engine = new ReliableTaskEngine(
                repository,
                new DefaultReliableTaskHandlerRegistry(List.of()),
                policy,
                clock,
                "owner-a"
        );
        ReliableTaskClient client = new DefaultReliableTaskClient(repository, engine, policy, () -> "task-2", clock);

        ReliableTask task = client.submit(ReliableTaskSubmission.builder("unknown", "{}").build()).task();
        ReliableTaskRunResult result = client.runNow(task.getKey());

        assertThat(result.finalTask().getStatus()).isEqualTo(ReliableTaskStatus.MANUAL);
        assertThat(result.finalTask().getLastErrorCode()).isEqualTo("MISSING_HANDLER");
    }

    @Test
    void shouldRequeueManualTaskWithExpectedVersionAndResetAttempts() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-09T00:00:00Z"));
        InMemoryRepository repository = new InMemoryRepository();
        ReliableTaskRuntimePolicy policy = ReliableTaskRuntimePolicy.defaults();
        ReliableTaskEngine engine = new ReliableTaskEngine(
                repository,
                new DefaultReliableTaskHandlerRegistry(List.of()),
                policy,
                clock,
                "owner-a"
        );
        ReliableTaskClient client = new DefaultReliableTaskClient(repository, engine, policy, () -> "task-3", clock);
        ReliableTask manual = client.runNow(
                client.submit(ReliableTaskSubmission.builder("unknown", "{}").build()).task().getKey()
        ).finalTask();

        DefaultReliableTaskAdminClient admin = new DefaultReliableTaskAdminClient(repository, clock);
        ReliableTaskAdminResult stale = admin.requeue(manual.getKey(), manual.getVersion() - 1, clock.instant());
        ReliableTaskAdminResult updated = admin.requeue(manual.getKey(), manual.getVersion(), clock.instant());

        assertThat(stale.status()).isEqualTo(ReliableTaskAdminStatus.STALE_VERSION);
        assertThat(updated.status()).isEqualTo(ReliableTaskAdminStatus.UPDATED);
        assertThat(updated.task().getStatus()).isEqualTo(ReliableTaskStatus.READY);
        assertThat(updated.task().getAttemptCount()).isZero();
    }

    private static final class MutableClock extends Clock {
        private Instant now;
        private MutableClock(Instant now) { this.now = now; }
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private static final class InMemoryRepository implements ReliableTaskRepository {
        private final Map<ReliableTaskKey, ReliableTask> tasks = new HashMap<>();

        @Override
        public synchronized ReliableTaskCreateResult create(ReliableTask task) {
            return tasks.putIfAbsent(task.getKey(), task) == null
                    ? ReliableTaskCreateResult.CREATED : ReliableTaskCreateResult.ALREADY_EXISTS;
        }

        @Override
        public synchronized Optional<ReliableTask> find(ReliableTaskKey key) {
            return Optional.ofNullable(tasks.get(key));
        }

        @Override
        public synchronized List<ReliableTask> findDue(ReliableTaskScanQuery query) {
            return tasks.values().stream()
                    .filter(task -> task.getStoreName().equals(query.storeName()))
                    .filter(task -> task.getScanBucket() == query.scanBucket())
                    .filter(task -> isDue(task, query.now()))
                    .limit(query.limit())
                    .toList();
        }

        @Override
        public synchronized ReliableTaskClaimResult tryClaim(ReliableTaskClaimCommand command) {
            ReliableTask candidate = command.candidate();
            ReliableTask current = tasks.get(candidate.getKey());
            if (current == null || current.getVersion() != candidate.getVersion()
                    || current.getStatus() != candidate.getStatus() || !isDue(current, command.now())) {
                return ReliableTaskClaimResult.missed();
            }
            ReliableTask claimed = copy(
                    current,
                    ReliableTaskStatus.RUNNING,
                    Math.min(current.getAttemptCount() + 1, current.getMaxAttempts()),
                    null,
                    command.ownerId(),
                    command.leaseUntil(),
                    current.getVersion() + 1,
                    current.getLastErrorCode(),
                    current.getLastErrorMessage(),
                    command.now(),
                    null
            );
            tasks.put(claimed.getKey(), claimed);
            return ReliableTaskClaimResult.claimed(claimed, current.getStatus());
        }

        @Override
        public synchronized boolean transition(ReliableTaskTransitionCommand command) {
            ReliableTask current = tasks.get(command.key());
            if (current == null || current.getStatus() != ReliableTaskStatus.RUNNING
                    || !Objects.equals(current.getOwnerId(), command.ownerId())
                    || current.getVersion() != command.expectedVersion()) {
                return false;
            }
            ReliableTask next = copy(
                    current,
                    command.targetStatus(),
                    current.getAttemptCount(),
                    command.nextExecuteAt(),
                    null,
                    null,
                    current.getVersion() + 1,
                    command.errorCode(),
                    command.errorMessage(),
                    command.now(),
                    command.targetStatus().isTerminal() ? command.now() : null
            );
            tasks.put(next.getKey(), next);
            return true;
        }

        @Override
        public synchronized boolean renewLease(ReliableTaskLeaseRenewCommand command) {
            ReliableTask current = tasks.get(command.key());
            if (current == null || current.getStatus() != ReliableTaskStatus.RUNNING
                    || !Objects.equals(current.getOwnerId(), command.ownerId())
                    || current.getVersion() != command.expectedVersion()
                    || !current.getLeaseUntil().isAfter(command.now())
                    || !command.leaseUntil().isAfter(current.getLeaseUntil())) {
                return false;
            }
            tasks.put(current.getKey(), copy(
                    current, current.getStatus(), current.getAttemptCount(), null,
                    current.getOwnerId(), command.leaseUntil(), current.getVersion(),
                    current.getLastErrorCode(), current.getLastErrorMessage(), command.now(), null));
            return true;
        }

        @Override
        public synchronized boolean adminTransition(ReliableTaskAdminTransitionCommand command) {
            ReliableTask current = tasks.get(command.key());
            if (current == null || current.getStatus() != command.expectedStatus()
                    || current.getVersion() != command.expectedVersion()) {
                return false;
            }
            ReliableTask updated = copy(
                    current,
                    command.targetStatus(),
                    command.resetAttempts() ? 0 : current.getAttemptCount(),
                    command.nextExecuteAt(),
                    null,
                    null,
                    current.getVersion() + 1,
                    null,
                    null,
                    command.now(),
                    command.targetStatus().isTerminal() ? command.now() : null
            );
            tasks.put(updated.getKey(), updated);
            return true;
        }

        private static boolean isDue(ReliableTask task, Instant now) {
            if (task.getStatus().isScheduledCandidate()) {
                return task.getNextExecuteAt() != null && !task.getNextExecuteAt().isAfter(now);
            }
            return task.getStatus() == ReliableTaskStatus.RUNNING
                    && task.getLeaseUntil() != null && !task.getLeaseUntil().isAfter(now);
        }

        private static ReliableTask copy(
                ReliableTask source,
                ReliableTaskStatus status,
                int attempts,
                Instant nextExecuteAt,
                String owner,
                Instant lease,
                long version,
                String errorCode,
                String errorMessage,
                Instant updatedAt,
                Instant completedAt) {
            return new ReliableTask(
                    source.getKey(), source.getTaskType(), source.getBusinessKey(), source.getPayload(), source.getRouteKey(),
                    source.getScanBucket(), status, attempts, source.getMaxAttempts(), nextExecuteAt, owner, lease, version,
                    errorCode, errorMessage, source.getCreatedAt(), updatedAt, completedAt);
        }
    }
}
