package com.xjtu.iron.relational.core.exception;

import com.xjtu.iron.relational.api.exception.RelationalAccessException;
import com.xjtu.iron.relational.api.exception.RelationalFailureType;
import com.xjtu.iron.relational.spi.exception.SqlExceptionTranslator;
import com.xjtu.iron.relational.spi.execution.SqlExecutionContext;

import java.sql.SQLDataException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLSyntaxErrorException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransactionRollbackException;
import java.sql.SQLTransientConnectionException;
import java.sql.SQLException;

/**
 * 基于标准 JDBC 异常层级与 SQLState class 的保守异常翻译器。
 *
 * <p>它不猜测数据库厂商特有 vendorCode。MySQL/PostgreSQL 的 duplicate-key、deadlock、
 * lock-timeout 等精细分类应在后续专用 Translator 中覆盖。</p>
 */
public final class StandardSqlExceptionTranslator implements SqlExceptionTranslator {

    @Override
    public RelationalAccessException translate(
            SqlExecutionContext context,
            SQLException exception
    ) {
        RelationalFailureType failureType = classify(exception);
        return new RelationalAccessException(
                failureType,
                context.operationName(),
                exception.getSQLState(),
                exception.getErrorCode(),
                buildMessage(context, exception, failureType),
                exception
        );
    }

    private RelationalFailureType classify(SQLException exception) {
        if (exception instanceof SQLTimeoutException) {
            return RelationalFailureType.TIMEOUT;
        }
        if (exception instanceof SQLTransientConnectionException
                || exception instanceof SQLNonTransientConnectionException
                || exception instanceof SQLRecoverableException) {
            return RelationalFailureType.CONNECTION_ERROR;
        }
        if (exception instanceof SQLSyntaxErrorException) {
            return RelationalFailureType.SQL_SYNTAX_ERROR;
        }
        if (exception instanceof SQLDataException) {
            return RelationalFailureType.DATA_ERROR;
        }
        if (exception instanceof SQLIntegrityConstraintViolationException) {
            return RelationalFailureType.CONSTRAINT_VIOLATION;
        }
        if (exception instanceof SQLTransactionRollbackException
                && "40001".equals(exception.getSQLState())) {
            return RelationalFailureType.SERIALIZATION_FAILURE;
        }

        String state = exception.getSQLState();
        if (state == null || state.length() < 2) {
            return RelationalFailureType.UNKNOWN;
        }
        if (state.startsWith("08")) {
            return RelationalFailureType.CONNECTION_ERROR;
        }
        if (state.startsWith("23")) {
            return RelationalFailureType.CONSTRAINT_VIOLATION;
        }
        if (state.startsWith("42")) {
            return RelationalFailureType.SQL_SYNTAX_ERROR;
        }
        if ("40001".equals(state)) {
            return RelationalFailureType.SERIALIZATION_FAILURE;
        }
        if (state.startsWith("HYT")) {
            return RelationalFailureType.TIMEOUT;
        }
        return RelationalFailureType.UNKNOWN;
    }

    private String buildMessage(
            SqlExecutionContext context,
            SQLException exception,
            RelationalFailureType failureType
    ) {
        return "Relational SQL execution failed, operation=" + context.operationName()
                + ", failureType=" + failureType
                + ", sqlState=" + exception.getSQLState()
                + ", vendorCode=" + exception.getErrorCode();
    }
}
