package com.xjtu.iron.reliable.task.core.client;

import com.xjtu.iron.foundation.id.api.StringIdGenerator;
import com.xjtu.iron.reliable.task.api.client.ReliableTaskClient;
import com.xjtu.iron.reliable.task.api.model.ReliableTask;
import com.xjtu.iron.reliable.task.api.model.ReliableTaskKey;
import com.xjtu.iron.reliable.task.api.operation.run.ReliableTaskRunResult;
import com.xjtu.iron.reliable.task.api.operation.run.ReliableTaskRunStatus;
import com.xjtu.iron.reliable.task.api.operation.submit.ReliableTaskSubmission;
import com.xjtu.iron.reliable.task.api.operation.submit.ReliableTaskSubmitResult;
import com.xjtu.iron.reliable.task.api.operation.submit.ReliableTaskSubmitStatus;
import com.xjtu.iron.reliable.task.api.repository.ReliableTaskRepository;
import com.xjtu.iron.reliable.task.api.repository.create.ReliableTaskCreateResult;
import com.xjtu.iron.reliable.task.api.state.ReliableTaskStatus;
import com.xjtu.iron.reliable.task.core.exception.ReliableTaskConflictException;
import com.xjtu.iron.reliable.task.core.execution.ReliableTaskEngine;
import com.xjtu.iron.reliable.task.core.policy.ReliableTaskRuntimePolicy;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** ReliableTaskClient 默认实现。 */
public final class DefaultReliableTaskClient implements ReliableTaskClient {

    /** 任务持久化仓储。 */
    private final ReliableTaskRepository repository;
    /** 执行任务主链路的引擎。 */
    private final ReliableTaskEngine engine;
    /** 任务创建和执行使用的运行时策略。 */
    private final ReliableTaskRuntimePolicy policy;
    /** 未指定任务 ID 时使用的字符串 ID 生成器。 */
    private final StringIdGenerator idGenerator;
    /** 为任务时间字段提供统一当前时间的时钟。 */
    private final Clock clock;

    public DefaultReliableTaskClient(
            ReliableTaskRepository repository,
            ReliableTaskEngine engine,
            ReliableTaskRuntimePolicy policy,
            StringIdGenerator idGenerator,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public ReliableTaskSubmitResult submit(ReliableTaskSubmission submission) {
        Objects.requireNonNull(submission, "submission must not be null");
        Instant now = clock.instant();
        String taskId = submission.getTaskId() == null ? idGenerator.nextId() : submission.getTaskId();
        int scanBucket = submission.getScanBucket() == null
                ? Math.floorMod(taskId.hashCode(), policy.getScanBucketCount())
                : submission.getScanBucket();
        if (scanBucket >= policy.getScanBucketCount()) {
            throw new IllegalArgumentException(
                    "scanBucket must be less than configured scanBucketCount=" + policy.getScanBucketCount()
            );
        }
        int maxAttempts = submission.getMaxAttempts() == null
                ? policy.getDefaultMaxAttempts()
                : submission.getMaxAttempts();
        ReliableTask task = new ReliableTask(
                new ReliableTaskKey(submission.getStoreName(), submission.getNamespace(), taskId),
                submission.getTaskType(),
                submission.getBusinessKey(),
                submission.getPayload(),
                submission.getRouteKey(),
                scanBucket,
                ReliableTaskStatus.READY,
                0,
                maxAttempts,
                submission.getFirstExecuteAt() == null ? now : submission.getFirstExecuteAt(),
                null,
                null,
                0,
                null,
                null,
                now,
                now,
                null
        );

        ReliableTaskCreateResult createResult = repository.create(task);
        if (createResult == ReliableTaskCreateResult.CREATED) {
            return new ReliableTaskSubmitResult(ReliableTaskSubmitStatus.CREATED, task);
        }

        ReliableTask existing = repository.find(task.getKey())
                .orElseThrow(() -> new IllegalStateException("task disappeared after duplicate create"));
        ensureSameLogicalTask(task, existing);
        return new ReliableTaskSubmitResult(ReliableTaskSubmitStatus.ALREADY_EXISTS, existing);
    }

    @Override
    public ReliableTaskRunResult runNow(ReliableTaskKey key) {
        ReliableTask candidate = repository.find(key).orElse(null);
        if (candidate == null) {
            return new ReliableTaskRunResult(ReliableTaskRunStatus.NOT_FOUND, null, null);
        }
        return engine.runCandidate(candidate);
    }

    @Override
    public Optional<ReliableTask> find(ReliableTaskKey key) {
        return repository.find(Objects.requireNonNull(key, "key must not be null"));
    }

    private static void ensureSameLogicalTask(ReliableTask expected, ReliableTask actual) {
        if (!expected.getTaskType().equals(actual.getTaskType())
                || !expected.getPayload().equals(actual.getPayload())
                || !Objects.equals(expected.getBusinessKey(), actual.getBusinessKey())
                || !Objects.equals(expected.getRouteKey(), actual.getRouteKey())
                || expected.getScanBucket() != actual.getScanBucket()
                || expected.getMaxAttempts() != actual.getMaxAttempts()) {
            throw new ReliableTaskConflictException(
                    "taskId already exists with different task content: " + expected.getTaskId()
            );
        }
    }
}
