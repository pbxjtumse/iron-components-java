package com.xjtu.iron.relational.core.execution;

import com.xjtu.iron.relational.api.statement.BatchSqlStatement;
import com.xjtu.iron.relational.api.statement.SqlExecutionOptions;
import com.xjtu.iron.relational.api.statement.SqlParameter;
import com.xjtu.iron.relational.api.statement.SqlStatement;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Relational Core 的输入防线。
 *
 * <p>只校验组件能够稳定判断的结构条件，不尝试解析 SQL，也不计算 ? 占位符数量。</p>
 */
public final class SqlStatementValidator {

    public void validate(SqlStatement statement) {
        Objects.requireNonNull(statement, "statement");
        validateCommon(statement.operationName(), statement.sql(), statement.options());
        validateParameters(statement.parameters(), "parameters");
    }

    public void validate(BatchSqlStatement statement) {
        Objects.requireNonNull(statement, "statement");
        validateCommon(statement.operationName(), statement.sql(), statement.options());
        for (int i = 0; i < statement.batches().size(); i++) {
            validateParameters(statement.batches().get(i), "batches[" + i + "]");
        }
    }

    private void validateCommon(
            String operationName,
            String sql,
            SqlExecutionOptions options
    ) {
        if (operationName == null || operationName.isBlank()) {
            throw new IllegalArgumentException("operationName must not be blank");
        }
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("sql must not be blank");
        }
        validateOptions(options);
    }

    private void validateOptions(SqlExecutionOptions options) {
        Objects.requireNonNull(options, "options");

        Duration timeout = options.timeout();
        if (timeout != null && timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }
        if (options.fetchSize() != null && options.fetchSize() < 0) {
            throw new IllegalArgumentException("fetchSize must be >= 0");
        }
        if (options.maxRows() != null && options.maxRows() < 0) {
            throw new IllegalArgumentException("maxRows must be >= 0");
        }
    }

    private void validateParameters(List<SqlParameter> parameters, String path) {
        Objects.requireNonNull(parameters, path);
        for (int i = 0; i < parameters.size(); i++) {
            if (parameters.get(i) == null) {
                throw new IllegalArgumentException(path + "[" + i + "] must not be null");
            }
        }
    }
}
