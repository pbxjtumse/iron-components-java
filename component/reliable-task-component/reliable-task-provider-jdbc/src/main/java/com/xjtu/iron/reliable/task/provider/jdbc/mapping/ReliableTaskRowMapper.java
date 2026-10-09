package com.xjtu.iron.reliable.task.provider.jdbc.mapping;

import com.xjtu.iron.relational.api.mapping.RowMapper;
import com.xjtu.iron.reliable.task.api.model.ReliableTask;
import com.xjtu.iron.reliable.task.api.model.ReliableTaskKey;
import com.xjtu.iron.reliable.task.api.state.ReliableTaskStatus;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

/** 将可靠任务表的一行数据映射为不可变任务快照。 */
public final class ReliableTaskRowMapper implements RowMapper<ReliableTask> {

    public static final ReliableTaskRowMapper INSTANCE = new ReliableTaskRowMapper();

    private ReliableTaskRowMapper() {
    }

    @Override
    public ReliableTask map(ResultSet resultSet) throws SQLException {
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

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
