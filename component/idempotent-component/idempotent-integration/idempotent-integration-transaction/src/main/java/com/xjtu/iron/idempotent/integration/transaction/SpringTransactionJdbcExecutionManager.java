package com.xjtu.iron.idempotent.integration.transaction;

import com.xjtu.iron.idempotent.provider.jdbc.execution.JdbcExecutionManager;
import com.xjtu.iron.idempotent.provider.jdbc.execution.JdbcWork;
import com.xjtu.iron.transaction.api.definition.TransactionOptions;
import com.xjtu.iron.transaction.api.definition.TransactionPropagation;
import com.xjtu.iron.transaction.api.execution.TransactionExecutor;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Objects;

/**
 * transaction-component 与 JDBC Repository 之间的 Connection 桥梁。
 *
 * <p>真正的关键不是“代码外面套了一个事务模板”，而是幂等 Repository 的 SQL 必须拿到业务事务绑定的同一条
 * Connection。否则 Business 用 Connection-A，markSuccess 又重新 dataSource.getConnection() 得到 Connection-B，
 * 表面上有事务，实际上仍然存在两个提交点。</p>
 *
 * <pre>
 * Tx-A / Tx-C：TransactionExecutor(REQUIRES_NEW) -> 当前事务绑定 Connection -> Repository SQL
 * Tx-B       ：TransactionCoordinator(REQUIRED) -> Business SQL -> markSuccess -> 复用同一 transaction-bound Connection
 * </pre>
 */
public final class SpringTransactionJdbcExecutionManager implements JdbcExecutionManager {

    private static final String STATE_TRANSACTION_NAME = "idempotency-state";

    private final DataSource dataSource;
    private final TransactionExecutor transactionExecutor;

    public SpringTransactionJdbcExecutionManager(DataSource dataSource, TransactionExecutor transactionExecutor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.transactionExecutor = Objects.requireNonNull(transactionExecutor, "transactionExecutor must not be null");
    }

    /**
     * 普通 JDBC 工作入口。
     *
     * <p>DataSourceUtils 会优先返回当前事务绑定的 Connection；如果当前没有事务，则正常获取并在 finally 中归还连接池。</p>
     */
    @Override
    public <T> T withConnection(JdbcWork<T> work) throws Exception {
        Objects.requireNonNull(work, "work must not be null");
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            return work.execute(connection);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    /**
     * Tx-B 完成阶段专用：强制要求当前存在真实本地事务，并确认该事务绑定的就是幂等 Repository 使用的 DataSource。
     *
     * <p>这里选择 fail-fast，而不是“拿不到当前事务就偷偷新取 Connection”，因为后者会制造虚假的原子性保证。</p>
     */
    @Override
    public <T> T inCurrentTransaction(JdbcWork<T> work) throws Exception {
        Objects.requireNonNull(work, "work must not be null");

        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("markSuccess requires an active local transaction, but no transaction is active");
        }

        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            if (!DataSourceUtils.isConnectionTransactional(connection, dataSource)) {
                throw new IllegalStateException(
                        "current transaction does not bind the idempotency DataSource; business DB and idempotency DB "
                                + "must use the same local transaction resource");
            }
            return work.execute(connection);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    /**
     * Tx-A / Tx-C 专用：通过 transaction-component 建立 REQUIRES_NEW，再复用这个新事务绑定的 Connection。
     *
     * <p>不要在这里自己 connection.setAutoCommit(false) 冒充事务模板，否则外层事务挂起、传播语义、异常结果都无法统一。</p>
     */
    @Override
    public <T> T inNewTransaction(JdbcWork<T> work) throws Exception {
        Objects.requireNonNull(work, "work must not be null");

        TransactionOptions options = TransactionOptions.builder()
                .name(STATE_TRANSACTION_NAME)
                .propagation(TransactionPropagation.REQUIRES_NEW)
                .build();

        try {
            return transactionExecutor.execute(options, context -> {
                try {
                    return inCurrentTransaction(work);
                } catch (RuntimeException | Error unchecked) {
                    throw unchecked;
                } catch (Exception checked) {
                    throw new CheckedJdbcWorkRuntimeException(checked);
                }
            });
        } catch (CheckedJdbcWorkRuntimeException checked) {
            throw checked.original;
        }
    }

    @Override
    public boolean supportsCurrentTransactionParticipation() {
        return true;
    }

    /** 仅用于跨过 transaction callback 的 checked-exception 边界。 */
    private static final class CheckedJdbcWorkRuntimeException extends RuntimeException {
        private final Exception original;

        private CheckedJdbcWorkRuntimeException(Exception original) {
            super(original);
            this.original = original;
        }
    }
}
