package com.xjtu.iron.reliable.task.provider.jdbc.repository;

import com.xjtu.iron.relational.api.RelationalTemplate;
import com.xjtu.iron.relational.api.exception.RelationalAccessException;
import com.xjtu.iron.relational.api.exception.RelationalFailureType;
import com.xjtu.iron.relational.api.statement.SqlStatement;
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
import com.xjtu.iron.reliable.task.provider.jdbc.mapping.ReliableTaskRowMapper;
import com.xjtu.iron.reliable.task.provider.jdbc.sql.ReliableTaskSqlStatements;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 基于 Relational Access 的固定表 JDBC Repository。 */
public final class JdbcReliableTaskRepository implements ReliableTaskRepository {

    /** 未显式配置时采用的可靠任务表名。 */
    public static final String DEFAULT_TABLE_NAME = ReliableTaskSqlStatements.DEFAULT_TABLE_NAME;

    /** 屏蔽底层 JDBC 方言和异常差异的关系型访问模板。 */
    private final RelationalTemplate relational;
    /** 当前表名对应的可靠任务 SQL 集合。 */
    private final ReliableTaskSqlStatements sql;

    public JdbcReliableTaskRepository(RelationalTemplate relational) {
        this(relational, DEFAULT_TABLE_NAME);
    }

    public JdbcReliableTaskRepository(RelationalTemplate relational, String table) {
        this.relational = Objects.requireNonNull(relational, "relational must not be null");
        this.sql = new ReliableTaskSqlStatements(table);
    }

    @Override
    public ReliableTaskCreateResult create(ReliableTask task) {
        Objects.requireNonNull(task, "task must not be null");
        try {
            relational.update(SqlStatement.of(
                    "reliable-task.create",
                    sql.insert(),
                    task.getStoreName(),
                    task.getNamespace(),
                    task.getTaskId(),
                    task.getTaskType(),
                    task.getBusinessKey(),
                    task.getPayload(),
                    task.getRouteKey(),
                    task.getScanBucket(),
                    task.getStatus().name(),
                    task.getAttemptCount(),
                    task.getMaxAttempts(),
                    timestamp(task.getNextExecuteAt()),
                    task.getOwnerId(),
                    timestamp(task.getLeaseUntil()),
                    task.getVersion(),
                    task.getLastErrorCode(),
                    task.getLastErrorMessage(),
                    timestamp(task.getCreatedAt()),
                    timestamp(task.getUpdatedAt()),
                    timestamp(task.getCompletedAt())
            ));
            return ReliableTaskCreateResult.CREATED;
        } catch (RelationalAccessException failure) {
            if (isConstraintConflict(failure) && find(task.getKey()).isPresent()) {
                return ReliableTaskCreateResult.ALREADY_EXISTS;
            }
            throw failure;
        }
    }

    @Override
    public Optional<ReliableTask> find(ReliableTaskKey key) {
        Objects.requireNonNull(key, "key must not be null");
        return relational.queryOne(
                SqlStatement.of("reliable-task.find", sql.find(), key.getStoreName(), key.getNamespace(), key.getTaskId()),
                ReliableTaskRowMapper.INSTANCE
        );
    }

    @Override
    public List<ReliableTask> findDue(ReliableTaskScanQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        Timestamp now = timestamp(query.getNow());
        return relational.queryList(
                SqlStatement.of(
                        "reliable-task.find-due",
                        sql.findDue(),
                        query.getStoreName(),
                        query.getScanBucket(),
                        now,
                        now,
                        query.getLimit()
                ),
                ReliableTaskRowMapper.INSTANCE
        );
    }

    @Override
    public ReliableTaskClaimResult tryClaim(ReliableTaskClaimCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        ReliableTask candidate = command.getCandidate();
        boolean expiredRunning = candidate.getStatus() == ReliableTaskStatus.RUNNING;
        long affected = relational.update(SqlStatement.of(
                "reliable-task.try-claim",
                sql.tryClaim(expiredRunning),
                command.getOwnerId(),
                timestamp(command.getLeaseUntil()),
                timestamp(command.getNow()),
                candidate.getStoreName(),
                candidate.getNamespace(),
                candidate.getTaskId(),
                candidate.getStatus().name(),
                candidate.getVersion(),
                timestamp(command.getNow())
        )).affectedRows();
        if (affected != 1) {
            return ReliableTaskClaimResult.missed();
        }

        ReliableTask claimed = new ReliableTask(
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
        return ReliableTaskClaimResult.claimed(claimed, candidate.getStatus());
    }

    @Override
    public boolean transition(ReliableTaskTransitionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        boolean terminal = command.getTargetStatus().isTerminal();
        return relational.update(SqlStatement.of(
                "reliable-task.transition",
                sql.transition(),
                command.getTargetStatus().name(),
                timestamp(command.getNextExecuteAt()),
                command.getErrorCode(),
                command.getErrorMessage(),
                timestamp(command.getNow()),
                terminal ? timestamp(command.getNow()) : null,
                command.getKey().getStoreName(),
                command.getKey().getNamespace(),
                command.getKey().getTaskId(),
                command.getOwnerId(),
                command.getExpectedVersion()
        )).affectedRows() == 1;
    }

    @Override
    public boolean renewLease(ReliableTaskLeaseRenewCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return relational.update(SqlStatement.of(
                "reliable-task.renew-lease",
                sql.renewLease(),
                timestamp(command.getLeaseUntil()),
                timestamp(command.getNow()),
                command.getKey().getStoreName(),
                command.getKey().getNamespace(),
                command.getKey().getTaskId(),
                command.getOwnerId(),
                command.getExpectedVersion(),
                timestamp(command.getNow()),
                timestamp(command.getLeaseUntil())
        )).affectedRows() == 1;
    }

    @Override
    public boolean adminTransition(ReliableTaskAdminTransitionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return relational.update(SqlStatement.of(
                "reliable-task.admin-transition",
                sql.adminTransition(command.isResetAttempts()),
                command.getTargetStatus().name(),
                timestamp(command.getNextExecuteAt()),
                timestamp(command.getNow()),
                command.getTargetStatus().isTerminal() ? timestamp(command.getNow()) : null,
                command.getKey().getStoreName(),
                command.getKey().getNamespace(),
                command.getKey().getTaskId(),
                command.getExpectedStatus().name(),
                command.getExpectedVersion()
        )).affectedRows() == 1;
    }

    private static boolean isConstraintConflict(RelationalAccessException failure) {
        return failure.failureType() == RelationalFailureType.DUPLICATE_KEY
                || failure.failureType() == RelationalFailureType.CONSTRAINT_VIOLATION;
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
