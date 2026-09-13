package com.xjtu.iron.relational.core.execution;

import com.xjtu.iron.relational.api.statement.SqlExecutionOptions;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;

/**
 * 将 SqlExecutionOptions 投影到 JDBC Statement。
 */
public final class JdbcStatementConfigurer {

    public void configure(
            PreparedStatement statement,
            SqlExecutionOptions options
    ) throws SQLException {
        if (options.timeout() != null) {
            statement.setQueryTimeout(toJdbcSeconds(options.timeout()));
        }
        if (options.fetchSize() != null) {
            statement.setFetchSize(options.fetchSize());
        }
        if (options.maxRows() != null) {
            statement.setMaxRows(options.maxRows());
        }
    }

    private int toJdbcSeconds(Duration timeout) {
        if (timeout.isZero()) {
            return 0;
        }

        long seconds = timeout.getSeconds();
        if (timeout.getNano() > 0) {
            seconds++;
        }
        return seconds > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) seconds;
    }
}
