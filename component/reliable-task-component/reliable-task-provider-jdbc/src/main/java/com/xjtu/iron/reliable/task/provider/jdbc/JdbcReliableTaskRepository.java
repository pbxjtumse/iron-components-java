package com.xjtu.iron.reliable.task.provider.jdbc;

import com.xjtu.iron.relational.api.RelationalTemplate;
import com.xjtu.iron.relational.api.exception.RelationalAccessException;
import com.xjtu.iron.relational.api.exception.RelationalFailureType;
import com.xjtu.iron.relational.api.statement.SqlStatement;
import com.xjtu.iron.reliable.task.api.model.ReliableTask;
import com.xjtu.iron.reliable.task.api.model.ReliableTaskKey;
import com.xjtu.iron.reliable.task.api.model.ReliableTaskStatus;
import com.xjtu.iron.reliable.task.api.repository.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** 基于 Relational Access 的固定表 JDBC Repository。 */
public final class JdbcReliableTaskRepository implements ReliableTaskRepository {

    public static final String DEFAULT_TABLE_NAME = "iron_reliable_task";
    private static final Pattern SAFE_TABLE = Pattern.compile("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)?");

    private final RelationalTemplate relational;
    private final String table;

    public JdbcReliableTaskRepository(RelationalTemplate relational) {
        this(relational, DEFAULT_TABLE_NAME);
    }

    public JdbcReliableTaskRepository(RelationalTemplate relational, String table) {
        this.relational = Objects.requireNonNull(relational, "relational must not be null");
        this.table = validateTable(table);
    }

    @Override
    public ReliableTaskCreateResult create(ReliableTask task) {
        Objects.requireNonNull(task, "task must not be null");
        String sql = "INSERT INTO " + table + " ("
                + "store_name,namespace,task_id,task_type,business_key,payload,route_key,scan_bucket,status,"
                + "attempt_count,max_attempts,next_execute_at,owner_id,lease_until,version,last_error_code,"
                + "last_error_message,created_at,updated_at,completed_at)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try {
            relational.update(SqlStatement.of(
                    "reliable-task.create",
                    sql,
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
        String sql = "SELECT " + columns() + " FROM " + table
                + " WHERE store_name=? AND namespace=? AND task_id=?";
        return relational.queryOne(
                SqlStatement.of("reliable-task.find", sql, key.storeName(), key.namespace(), key.taskId()),
                this::map
        );
    }

    @Override
    public List<ReliableTask> findDue(ReliableTaskScanQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        String sql = "SELECT " + columns() + " FROM " + table
                + " WHERE store_name=? AND scan_bucket=? AND ("
                + "(status IN ('READY','RETRY_WAIT','WAIT_RECONCILE') AND next_execute_at<=?)"
                + " OR (status='RUNNING' AND lease_until<=?))"
                + " ORDER BY COALESCE(next_execute_at,lease_until),created_at LIMIT ?";
        Timestamp now = timestamp(query.now());
        return relational.queryList(
                SqlStatement.of(
                        "reliable-task.find-due",
                        sql,
                        query.storeName(),
                        query.scanBucket(),
                        now,
                        now,
                        query.limit()
                ),
                this::map
        );
    }

    @Override
    public ReliableTaskClaimResult tryClaim(ReliableTaskClaimCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        ReliableTask candidate = command.candidate();
        boolean expiredRunning = candidate.getStatus() == ReliableTaskStatus.RUNNING;
        String timeColumn = expiredRunning ? "lease_until" : "next_execute_at";
        String sql = "UPDATE " + table
                + " SET status='RUNNING',owner_id=?,lease_until=?,next_execute_at=NULL,"
                + "attempt_count=CASE WHEN attempt_count < max_attempts THEN attempt_count+1 ELSE attempt_count END,"
                + "version=version+1,updated_at=?"
                + " WHERE store_name=? AND namespace=? AND task_id=? AND status=? AND version=?"
                + " AND " + timeColumn + "<=?";
        long affected = relational.update(SqlStatement.of(
                "reliable-task.try-claim",
                sql,
                command.ownerId(),
                timestamp(command.leaseUntil()),
                timestamp(command.now()),
                candidate.getStoreName(),
                candidate.getNamespace(),
                candidate.getTaskId(),
                candidate.getStatus().name(),
                candidate.getVersion(),
                timestamp(command.now())
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
                command.ownerId(),
                command.leaseUntil(),
                candidate.getVersion() + 1,
                candidate.getLastErrorCode(),
                candidate.getLastErrorMessage(),
                candidate.getCreatedAt(),
                command.now(),
                null
        );
        return ReliableTaskClaimResult.claimed(claimed, candidate.getStatus());
    }

    @Override
    public boolean transition(ReliableTaskTransitionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        boolean terminal = command.targetStatus().isTerminal();
        String sql = "UPDATE " + table
                + " SET status=?,next_execute_at=?,owner_id=NULL,lease_until=NULL,version=version+1,"
                + "last_error_code=?,last_error_message=?,updated_at=?,completed_at=?"
                + " WHERE store_name=? AND namespace=? AND task_id=?"
                + " AND status='RUNNING' AND owner_id=? AND version=?";
        return relational.update(SqlStatement.of(
                "reliable-task.transition",
                sql,
                command.targetStatus().name(),
                timestamp(command.nextExecuteAt()),
                command.errorCode(),
                command.errorMessage(),
                timestamp(command.now()),
                terminal ? timestamp(command.now()) : null,
                command.key().storeName(),
                command.key().namespace(),
                command.key().taskId(),
                command.ownerId(),
                command.expectedVersion()
        )).affectedRows() == 1;
    }

    @Override
    public boolean renewLease(ReliableTaskLeaseRenewCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        String sql = "UPDATE " + table
                + " SET lease_until=?,updated_at=?"
                + " WHERE store_name=? AND namespace=? AND task_id=?"
                + " AND status='RUNNING' AND owner_id=? AND version=? AND lease_until>? AND lease_until<?";
        return relational.update(SqlStatement.of(
                "reliable-task.renew-lease",
                sql,
                timestamp(command.leaseUntil()),
                timestamp(command.now()),
                command.key().storeName(),
                command.key().namespace(),
                command.key().taskId(),
                command.ownerId(),
                command.expectedVersion(),
                timestamp(command.now()),
                timestamp(command.leaseUntil())
        )).affectedRows() == 1;
    }

    @Override
    public boolean adminTransition(ReliableTaskAdminTransitionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        String attemptExpression = command.resetAttempts() ? "0" : "attempt_count";
        String sql = "UPDATE " + table
                + " SET status=?,next_execute_at=?,owner_id=NULL,lease_until=NULL,attempt_count="
                + attemptExpression
                + ",version=version+1,last_error_code=NULL,last_error_message=NULL,updated_at=?,completed_at=?"
                + " WHERE store_name=? AND namespace=? AND task_id=? AND status=? AND version=?";
        return relational.update(SqlStatement.of(
                "reliable-task.admin-transition",
                sql,
                command.targetStatus().name(),
                timestamp(command.nextExecuteAt()),
                timestamp(command.now()),
                command.targetStatus().isTerminal() ? timestamp(command.now()) : null,
                command.key().storeName(),
                command.key().namespace(),
                command.key().taskId(),
                command.expectedStatus().name(),
                command.expectedVersion()
        )).affectedRows() == 1;
    }

    private ReliableTask map(ResultSet resultSet) throws SQLException {
        return new ReliableTask(
                new ReliableTaskKey(
                        resultSet.getString("store_name"),
                        resultSet.getString("namespace"),
                        resultSet.getString("task_id")
                ),
                resultSet.getString("task_type"),
                resultSet.getString("business_key"),
                resultSet.getString("payload"),
                resultSet.getString("route_key"),
                resultSet.getInt("scan_bucket"),
                ReliableTaskStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("attempt_count"),
                resultSet.getInt("max_attempts"),
                instant(resultSet.getTimestamp("next_execute_at")),
                resultSet.getString("owner_id"),
                instant(resultSet.getTimestamp("lease_until")),
                resultSet.getLong("version"),
                resultSet.getString("last_error_code"),
                resultSet.getString("last_error_message"),
                instant(resultSet.getTimestamp("created_at")),
                instant(resultSet.getTimestamp("updated_at")),
                instant(resultSet.getTimestamp("completed_at"))
        );
    }

    private static String columns() {
        return "store_name,namespace,task_id,task_type,business_key,payload,route_key,scan_bucket,status,"
                + "attempt_count,max_attempts,next_execute_at,owner_id,lease_until,version,last_error_code,"
                + "last_error_message,created_at,updated_at,completed_at";
    }

    private static boolean isConstraintConflict(RelationalAccessException failure) {
        return failure.failureType() == RelationalFailureType.DUPLICATE_KEY
                || failure.failureType() == RelationalFailureType.CONSTRAINT_VIOLATION;
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static String validateTable(String table) {
        if (table == null || !SAFE_TABLE.matcher(table.trim()).matches()) {
            throw new IllegalArgumentException("invalid reliable task table name: " + table);
        }
        return table.trim();
    }
}
