package com.xjtu.iron.reliable.task.provider.mybatis.repository;

import com.xjtu.iron.reliable.task.api.model.ReliableTask;
import com.xjtu.iron.reliable.task.api.model.ReliableTaskKey;
import com.xjtu.iron.reliable.task.api.repository.ReliableTaskRepository;
import com.xjtu.iron.reliable.task.api.repository.claim.ReliableTaskClaimCommand;
import com.xjtu.iron.reliable.task.api.repository.claim.ReliableTaskClaimResult;
import com.xjtu.iron.reliable.task.api.repository.create.ReliableTaskCreateResult;
import com.xjtu.iron.reliable.task.api.repository.lease.ReliableTaskLeaseRenewCommand;
import com.xjtu.iron.reliable.task.api.repository.scan.ReliableTaskScanQuery;
import com.xjtu.iron.reliable.task.api.repository.transition.ReliableTaskAdminTransitionCommand;
import com.xjtu.iron.reliable.task.api.repository.transition.ReliableTaskTransitionCommand;
import com.xjtu.iron.reliable.task.api.state.ReliableTaskStatus;
import com.xjtu.iron.reliable.task.provider.mybatis.mapper.ReliableTaskMapper;
import com.xjtu.iron.reliable.task.provider.mybatis.mapping.ReliableTaskRow;
import com.xjtu.iron.reliable.task.provider.mybatis.sql.MyBatisReliableTaskTable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 基于 MyBatis Mapper 的 ReliableTaskRepository 实现。 */
public final class MyBatisReliableTaskRepository implements ReliableTaskRepository {

    /** 执行可靠任务固定 SQL 的 MyBatis Mapper。 */
    private final ReliableTaskMapper mapper;
    /** 经过白名单校验、允许安全展开到 Mapper XML 的表名。 */
    private final String tableName;

    public MyBatisReliableTaskRepository(ReliableTaskMapper mapper) {
        this(mapper, MyBatisReliableTaskTable.DEFAULT_TABLE_NAME);
    }

    public MyBatisReliableTaskRepository(ReliableTaskMapper mapper, String tableName) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.tableName = MyBatisReliableTaskTable.validate(tableName);
    }

    @Override
    public ReliableTaskCreateResult create(ReliableTask task) {
        Objects.requireNonNull(task, "task must not be null");
        try {
            mapper.insert(tableName, task);
            return ReliableTaskCreateResult.CREATED;
        } catch (RuntimeException failure) {
            if (MyBatisConstraintViolationDetector.isConstraintViolation(failure)
                    && find(task.getKey()).isPresent()) {
                return ReliableTaskCreateResult.ALREADY_EXISTS;
            }
            throw failure;
        }
    }

    @Override
    public Optional<ReliableTask> find(ReliableTaskKey key) {
        Objects.requireNonNull(key, "key must not be null");
        ReliableTaskRow row = mapper.find(
                tableName,
                key.getStoreName(),
                key.getNamespace(),
                key.getTaskId()
        );
        return row == null ? Optional.empty() : Optional.of(row.toDomain());
    }

    @Override
    public List<ReliableTask> findDue(ReliableTaskScanQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        return mapper.findDue(
                        tableName,
                        query.getStoreName(),
                        query.getScanBucket(),
                        query.getNow(),
                        query.getLimit()
                ).stream()
                .map(ReliableTaskRow::toDomain)
                .toList();
    }

    @Override
    public ReliableTaskClaimResult tryClaim(ReliableTaskClaimCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        ReliableTask candidate = command.getCandidate();
        boolean expiredRunning = candidate.getStatus() == ReliableTaskStatus.RUNNING;
        int affectedRows = mapper.tryClaim(
                tableName,
                candidate,
                command.getOwnerId(),
                command.getNow(),
                command.getLeaseUntil(),
                expiredRunning
        );
        if (affectedRows != 1) {
            return ReliableTaskClaimResult.missed();
        }

        ReliableTask claimed = claimedSnapshot(candidate, command);
        return ReliableTaskClaimResult.claimed(claimed, candidate.getStatus());
    }

    @Override
    public boolean transition(ReliableTaskTransitionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Instant completedAt = command.getTargetStatus().isTerminal() ? command.getNow() : null;
        return mapper.transition(
                tableName,
                command.getKey().getStoreName(),
                command.getKey().getNamespace(),
                command.getKey().getTaskId(),
                command.getOwnerId(),
                command.getExpectedVersion(),
                command.getTargetStatus().name(),
                command.getNextExecuteAt(),
                command.getErrorCode(),
                command.getErrorMessage(),
                command.getNow(),
                completedAt
        ) == 1;
    }

    @Override
    public boolean renewLease(ReliableTaskLeaseRenewCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return mapper.renewLease(
                tableName,
                command.getKey().getStoreName(),
                command.getKey().getNamespace(),
                command.getKey().getTaskId(),
                command.getOwnerId(),
                command.getExpectedVersion(),
                command.getNow(),
                command.getLeaseUntil()
        ) == 1;
    }

    @Override
    public boolean adminTransition(ReliableTaskAdminTransitionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Instant completedAt = command.getTargetStatus().isTerminal() ? command.getNow() : null;
        return mapper.adminTransition(
                tableName,
                command.getKey().getStoreName(),
                command.getKey().getNamespace(),
                command.getKey().getTaskId(),
                command.getExpectedStatus().name(),
                command.getExpectedVersion(),
                command.getTargetStatus().name(),
                command.getNextExecuteAt(),
                command.isResetAttempts(),
                command.getNow(),
                completedAt
        ) == 1;
    }

    private static ReliableTask claimedSnapshot(
            ReliableTask candidate,
            ReliableTaskClaimCommand command) {
        return new ReliableTask(
                candidate.getKey(),
                candidate.getTaskType(),
                candidate.getBusinessKey(),
                candidate.getPayload(),
                candidate.getRouteKey(),
                candidate.getScanBucket(),
                ReliableTaskStatus.RUNNING,
                Math.min(candidate.getAttemptCount() + 1, candidate.getMaxAttempts()),
                candidate.getMaxAttempts(),
                null,
                command.getOwnerId(),
                command.getLeaseUntil(),
                candidate.getVersion() + 1,
                candidate.getLastErrorCode(),
                candidate.getLastErrorMessage(),
                candidate.getCreatedAt(),
                command.getNow(),
                null
        );
    }
}
