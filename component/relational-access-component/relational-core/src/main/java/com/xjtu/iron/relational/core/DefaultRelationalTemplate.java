package com.xjtu.iron.relational.core;

import com.xjtu.iron.relational.api.RelationalTemplate;
import com.xjtu.iron.relational.api.exception.RelationalAccessException;
import com.xjtu.iron.relational.api.exception.RelationalFailureType;
import com.xjtu.iron.relational.api.mapping.RowMapper;
import com.xjtu.iron.relational.api.result.BatchResult;
import com.xjtu.iron.relational.api.result.GeneratedKey;
import com.xjtu.iron.relational.api.result.UpdateResult;
import com.xjtu.iron.relational.api.statement.BatchSqlStatement;
import com.xjtu.iron.relational.api.statement.SqlStatement;
import com.xjtu.iron.relational.core.execution.JdbcParameterBinder;
import com.xjtu.iron.relational.core.execution.JdbcStatementConfigurer;
import com.xjtu.iron.relational.core.execution.SqlStatementValidator;
import com.xjtu.iron.relational.spi.ConnectionHandle;
import com.xjtu.iron.relational.spi.ConnectionProvider;
import com.xjtu.iron.relational.spi.SqlExceptionTranslator;
import com.xjtu.iron.relational.spi.SqlExecutionContext;
import com.xjtu.iron.relational.spi.SqlExecutionKind;
import com.xjtu.iron.relational.spi.SqlExecutionListener;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * RelationalTemplate 的默认 JDBC 实现。
 *
 * <p>该类是整个 relational-core 的主执行模板，负责固定以下生命周期：</p>
 *
 * <pre>
 * validate
 *   -> build context
 *   -> listener.before
 *   -> acquire ConnectionHandle
 *   -> prepare statement
 *   -> configure statement
 *   -> bind parameters
 *   -> execute
 *   -> map/build result
 *   -> close JDBC resources
 *   -> close ConnectionHandle
 *   -> listener.afterSuccess/afterFailure
 * </pre>
 *
 * <p>它不理解幂等、Outbox、订单等上层语义，也不负责事务 begin/commit/rollback、
 * shard 计算和自动 retry。</p>
 */
public final class DefaultRelationalTemplate implements RelationalTemplate {

    private static final SqlExecutionListener NOOP_LISTENER = new SqlExecutionListener() {
    };

    private final ConnectionProvider connectionProvider;
    private final SqlExceptionTranslator exceptionTranslator;
    private final SqlExecutionListener executionListener;
    private final SqlStatementValidator validator;
    private final JdbcStatementConfigurer statementConfigurer;
    private final JdbcParameterBinder parameterBinder;

    public DefaultRelationalTemplate(
            ConnectionProvider connectionProvider,
            SqlExceptionTranslator exceptionTranslator
    ) {
        this(connectionProvider, exceptionTranslator, NOOP_LISTENER);
    }

    public DefaultRelationalTemplate(
            ConnectionProvider connectionProvider,
            SqlExceptionTranslator exceptionTranslator,
            SqlExecutionListener executionListener
    ) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
        this.exceptionTranslator = Objects.requireNonNull(exceptionTranslator, "exceptionTranslator");
        this.executionListener = executionListener == null ? NOOP_LISTENER : executionListener;
        this.validator = new SqlStatementValidator();
        this.statementConfigurer = new JdbcStatementConfigurer();
        this.parameterBinder = new JdbcParameterBinder();
    }

    @Override
    public <T> Optional<T> queryOne(SqlStatement statement, RowMapper<T> rowMapper) {
        validator.validate(statement);
        Objects.requireNonNull(rowMapper, "rowMapper");

        SqlExecutionContext context = contextOf(statement, SqlExecutionKind.QUERY_ONE);
        return execute(context, connection -> {
            try (PreparedStatement preparedStatement = connection.prepareStatement(statement.sql())) {
                statementConfigurer.configure(preparedStatement, statement.options());
                parameterBinder.bind(preparedStatement, statement.parameters());

                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }

                    T value = mapRow(context, rowMapper, resultSet);
                    if (resultSet.next()) {
                        throw nonUniqueResult(context);
                    }
                    return Optional.of(value);
                }
            }
        });
    }

    @Override
    public <T> List<T> queryList(SqlStatement statement, RowMapper<T> rowMapper) {
        validator.validate(statement);
        Objects.requireNonNull(rowMapper, "rowMapper");

        SqlExecutionContext context = contextOf(statement, SqlExecutionKind.QUERY_LIST);
        return execute(context, connection -> {
            try (PreparedStatement preparedStatement = connection.prepareStatement(statement.sql())) {
                statementConfigurer.configure(preparedStatement, statement.options());
                parameterBinder.bind(preparedStatement, statement.parameters());

                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    List<T> values = new ArrayList<>();
                    while (resultSet.next()) {
                        values.add(mapRow(context, rowMapper, resultSet));
                    }
                    return List.copyOf(values);
                }
            }
        });
    }

    @Override
    public <T> Optional<T> queryScalar(SqlStatement statement, Class<T> requiredType) {
        validator.validate(statement);
        Objects.requireNonNull(requiredType, "requiredType");

        SqlExecutionContext context = contextOf(statement, SqlExecutionKind.QUERY_SCALAR);
        return execute(context, connection -> {
            try (PreparedStatement preparedStatement = connection.prepareStatement(statement.sql())) {
                statementConfigurer.configure(preparedStatement, statement.options());
                parameterBinder.bind(preparedStatement, statement.parameters());

                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }

                    T value = resultSet.getObject(1, requiredType);
                    if (resultSet.next()) {
                        throw nonUniqueResult(context);
                    }
                    return Optional.ofNullable(value);
                }
            }
        });
    }

    @Override
    public UpdateResult update(SqlStatement statement) {
        validator.validate(statement);
        SqlExecutionContext context = contextOf(statement, SqlExecutionKind.UPDATE);

        return execute(context, connection -> {
            try (PreparedStatement preparedStatement = connection.prepareStatement(statement.sql())) {
                statementConfigurer.configure(preparedStatement, statement.options());
                parameterBinder.bind(preparedStatement, statement.parameters());
                return new UpdateResult(preparedStatement.executeUpdate());
            }
        });
    }

    @Override
    public <K> GeneratedKey<K> insertAndReturnKey(SqlStatement statement, Class<K> keyType) {
        validator.validate(statement);
        Objects.requireNonNull(keyType, "keyType");
        SqlExecutionContext context = contextOf(statement, SqlExecutionKind.INSERT_WITH_GENERATED_KEY);

        return execute(context, connection -> {
            try (PreparedStatement preparedStatement = connection.prepareStatement(
                    statement.sql(),
                    Statement.RETURN_GENERATED_KEYS
            )) {
                statementConfigurer.configure(preparedStatement, statement.options());
                parameterBinder.bind(preparedStatement, statement.parameters());
                preparedStatement.executeUpdate();

                try (ResultSet keys = preparedStatement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw generatedKeyUnavailable(context);
                    }
                    K value = keys.getObject(1, keyType);
                    if (value == null) {
                        throw generatedKeyUnavailable(context);
                    }
                    return new GeneratedKey<>(value);
                }
            }
        });
    }

    @Override
    public BatchResult batchUpdate(BatchSqlStatement statement) {
        validator.validate(statement);
        SqlExecutionContext context = contextOf(statement, SqlExecutionKind.BATCH);

        return execute(context, connection -> {
            try (PreparedStatement preparedStatement = connection.prepareStatement(statement.sql())) {
                statementConfigurer.configure(preparedStatement, statement.options());
                for (var batchParameters : statement.batches()) {
                    parameterBinder.bind(preparedStatement, batchParameters);
                    preparedStatement.addBatch();
                }
                return new BatchResult(preparedStatement.executeBatch());
            }
        });
    }

    private <T> T execute(SqlExecutionContext context, SqlWork<T> work) {
        long startedNanos = System.nanoTime();
        safeBefore(context);

        try {
            T result;
            try (ConnectionHandle handle = connectionProvider.acquire(context)) {
                Connection connection = Objects.requireNonNull(
                        handle.connection(),
                        "ConnectionHandle.connection() must not return null"
                );
                result = work.execute(connection);
            }

            safeAfterSuccess(context, elapsedSince(startedNanos));
            return result;
        } catch (RelationalAccessException exception) {
            safeAfterFailure(context, elapsedSince(startedNanos), exception);
            throw exception;
        } catch (SQLException exception) {
            RelationalAccessException translated = translate(context, exception);
            safeAfterFailure(context, elapsedSince(startedNanos), translated);
            throw translated;
        } catch (RuntimeException exception) {
            RelationalAccessException wrapped = new RelationalAccessException(
                    RelationalFailureType.UNKNOWN,
                    context.operationName(),
                    null,
                    null,
                    "Unexpected relational execution failure, operation=" + context.operationName(),
                    exception
            );
            safeAfterFailure(context, elapsedSince(startedNanos), wrapped);
            throw wrapped;
        }
    }

    private <T> T mapRow(
            SqlExecutionContext context,
            RowMapper<T> rowMapper,
            ResultSet resultSet
    ) {
        try {
            T value = rowMapper.map(resultSet);
            if (value == null) {
                throw new RelationalAccessException(
                        RelationalFailureType.RESULT_MAPPING_ERROR,
                        context.operationName(),
                        null,
                        null,
                        "RowMapper must not return null, operation=" + context.operationName(),
                        null
                );
            }
            return value;
        } catch (RelationalAccessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RelationalAccessException(
                    RelationalFailureType.RESULT_MAPPING_ERROR,
                    context.operationName(),
                    exception instanceof SQLException sqlException ? sqlException.getSQLState() : null,
                    exception instanceof SQLException sqlException ? sqlException.getErrorCode() : null,
                    "Failed to map relational result, operation=" + context.operationName(),
                    exception
            );
        }
    }

    private RelationalAccessException translate(
            SqlExecutionContext context,
            SQLException exception
    ) {
        RelationalAccessException translated = exceptionTranslator.translate(context, exception);
        if (translated != null) {
            return translated;
        }
        return new RelationalAccessException(
                RelationalFailureType.UNKNOWN,
                context.operationName(),
                exception.getSQLState(),
                exception.getErrorCode(),
                "Relational SQL execution failed, operation=" + context.operationName(),
                exception
        );
    }

    private RelationalAccessException nonUniqueResult(SqlExecutionContext context) {
        return new RelationalAccessException(
                RelationalFailureType.NON_UNIQUE_RESULT,
                context.operationName(),
                null,
                null,
                "Expected at most one row but query returned multiple rows, operation="
                        + context.operationName(),
                null
        );
    }

    private RelationalAccessException generatedKeyUnavailable(SqlExecutionContext context) {
        return new RelationalAccessException(
                RelationalFailureType.GENERATED_KEY_UNAVAILABLE,
                context.operationName(),
                null,
                null,
                "Database did not return a generated key, operation=" + context.operationName(),
                null
        );
    }

    private SqlExecutionContext contextOf(SqlStatement statement, SqlExecutionKind kind) {
        return new SqlExecutionContext(
                statement.operationName(),
                kind,
                statement.sql(),
                statement.route(),
                statement.options(),
                Map.of()
        );
    }

    private SqlExecutionContext contextOf(BatchSqlStatement statement, SqlExecutionKind kind) {
        return new SqlExecutionContext(
                statement.operationName(),
                kind,
                statement.sql(),
                statement.route(),
                statement.options(),
                Map.of("batchSize", statement.batches().size())
        );
    }

    private void safeBefore(SqlExecutionContext context) {
        try {
            executionListener.beforeExecute(context);
        } catch (RuntimeException ignored) {
            // Observation is a side channel and must not break SQL execution.
        }
    }

    private void safeAfterSuccess(SqlExecutionContext context, Duration elapsed) {
        try {
            executionListener.afterSuccess(context, elapsed);
        } catch (RuntimeException ignored) {
            // Observation is a side channel and must not rewrite a successful SQL outcome.
        }
    }

    private void safeAfterFailure(
            SqlExecutionContext context,
            Duration elapsed,
            Throwable failure
    ) {
        try {
            executionListener.afterFailure(context, elapsed, failure);
        } catch (RuntimeException ignored) {
            // Preserve the original relational failure.
        }
    }

    private Duration elapsedSince(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos);
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T execute(Connection connection) throws SQLException;
    }
}
