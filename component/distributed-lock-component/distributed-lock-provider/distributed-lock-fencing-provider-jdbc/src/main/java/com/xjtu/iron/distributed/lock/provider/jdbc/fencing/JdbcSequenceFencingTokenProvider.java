package com.xjtu.iron.distributed.lock.provider.jdbc.fencing;

import com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenProvider;
import com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenRequest;
import com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenResponse;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 基于 JDBC 表行序列的 fencing token Provider。
 *
 * <p>每个 {@code namespace + lock_name} 对应一行 current_token。生成 token 时在本地事务中：</p>
 * <ol>
 *     <li>优先执行原子 UPDATE current_token = current_token + 1；</li>
 *     <li>行不存在时 INSERT current_token = 1；</li>
 *     <li>并发 INSERT 冲突时回滚并重试；</li>
 *     <li>在事务提交前读取本事务生成的 token。</li>
 * </ol>
 *
 * <p>该实现只依赖标准 JDBC，不绑定 Spring JdbcTemplate，可在 Spring 和非 Spring 环境复用。</p>
 */
public final class JdbcSequenceFencingTokenProvider implements FencingTokenProvider {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final DataSource dataSource;
    private final String tableName;
    private final int maxRetries;
    private final String updateSql;
    private final String insertSql;
    private final String selectSql;

    public JdbcSequenceFencingTokenProvider(DataSource dataSource) {
        this(dataSource, JdbcFencingTokenConstants.DEFAULT_TABLE_NAME, JdbcFencingTokenConstants.DEFAULT_MAX_RETRIES);
    }

    public JdbcSequenceFencingTokenProvider(DataSource dataSource, String tableName, int maxRetries) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.tableName = validateTableName(tableName);
        if (maxRetries <= 0) {
            throw new IllegalArgumentException("maxRetries must be positive");
        }
        this.maxRetries = maxRetries;
        this.updateSql = "UPDATE " + this.tableName
                + " SET current_token = current_token + 1, updated_at = CURRENT_TIMESTAMP"
                + " WHERE namespace = ? AND lock_name = ?";
        this.insertSql = "INSERT INTO " + this.tableName
                + " (namespace, lock_name, current_token, updated_at) VALUES (?, ?, 1, CURRENT_TIMESTAMP)";
        this.selectSql = "SELECT current_token FROM " + this.tableName
                + " WHERE namespace = ? AND lock_name = ?";
    }

    @Override
    public String providerName() {
        return JdbcFencingTokenConstants.PROVIDER_NAME;
    }

    @Override
    public boolean supports(FencingTokenRequest request) {
        return request != null
                && request.getNamespace().length() <= JdbcFencingTokenConstants.MAX_NAMESPACE_LENGTH
                && request.getLockName().length() <= JdbcFencingTokenConstants.MAX_LOCK_NAME_LENGTH;
    }

    @Override
    public FencingTokenResponse nextToken(FencingTokenRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        SQLException lastDuplicate = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return FencingTokenResponse.issued(nextTokenInTransaction(request.getNamespace(), request.getLockName()));
            } catch (SQLException error) {
                if (isDuplicateKey(error) && attempt < maxRetries) {
                    lastDuplicate = error;
                    continue;
                }
                return FencingTokenResponse.failed(error,
                        "failed to issue JDBC fencing token, provider=" + providerName() + ", table=" + tableName + ", attempt=" + attempt);
            }
        }
        return FencingTokenResponse.failed(lastDuplicate, "failed to issue JDBC fencing token after retries: " + maxRetries);
    }

    private long nextTokenInTransaction(String namespace, String lockName) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                int updated = incrementExisting(connection, namespace, lockName);
                if (updated == 0) {
                    insertInitial(connection, namespace, lockName);
                }
                long token = selectCurrent(connection, namespace, lockName);
                if (token <= 0L) {
                    throw new SQLException("generated fencing token must be positive: " + token);
                }
                connection.commit();
                return token;
            } catch (SQLException | RuntimeException error) {
                rollbackQuietly(connection, error);
                throw error;
            } finally {
                restoreAutoCommitQuietly(connection, originalAutoCommit);
            }
        }
    }

    private int incrementExisting(Connection connection, String namespace, String lockName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(updateSql)) {
            statement.setString(1, namespace);
            statement.setString(2, lockName);
            return statement.executeUpdate();
        }
    }

    private void insertInitial(Connection connection, String namespace, String lockName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
            statement.setString(1, namespace);
            statement.setString(2, lockName);
            statement.executeUpdate();
        }
    }

    private long selectCurrent(Connection connection, String namespace, String lockName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(selectSql)) {
            statement.setString(1, namespace);
            statement.setString(2, lockName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("fencing token row not found after increment");
                }
                return resultSet.getLong(1);
            }
        }
    }

    private static boolean isDuplicateKey(SQLException error) {
        for (SQLException current = error; current != null; current = current.getNextException()) {
            if (current instanceof SQLIntegrityConstraintViolationException) {
                return true;
            }
            String sqlState = current.getSQLState();
            if (sqlState != null && sqlState.startsWith("23")) {
                return true;
            }
        }
        return false;
    }

    private static void rollbackQuietly(Connection connection, Throwable original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackError) {
            original.addSuppressed(rollbackError);
        }
    }

    private static void restoreAutoCommitQuietly(Connection connection, boolean autoCommit) {
        try {
            connection.setAutoCommit(autoCommit);
        } catch (SQLException ignored) {
            // connection 即将关闭，不覆盖原始结果。
        }
    }

    private static String validateTableName(String tableName) {
        if (tableName == null || !SAFE_IDENTIFIER.matcher(tableName.trim()).matches()) {
            throw new IllegalArgumentException("tableName must be a simple SQL identifier: " + tableName);
        }
        return tableName.trim();
    }
}
