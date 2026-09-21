package com.xjtu.iron.idempotent.integration.transaction;

import com.xjtu.iron.idempotent.provider.jdbc.execution.JdbcExecutionManager;
import com.xjtu.iron.transaction.api.definition.TransactionOptions;
import com.xjtu.iron.transaction.api.execution.TransactionCallback;
import com.xjtu.iron.transaction.api.execution.TransactionExecutor;
import com.xjtu.iron.transaction.core.executor.DefaultTransactionExecutor;
import com.xjtu.iron.transaction.provider.spring.transaction.SpringTransactionProvider;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.sql.DataSource;
import java.util.Objects;

/** 一份物理 DataSource 同时生成 Tx-A/B/C 执行器与 JDBC manager，避免两张独立 Map 配错资源。 */
public final class DirectStorageResource {
    private final String dataSourceKey;
    private final DataSource dataSource;
    private final TransactionExecutor transactionExecutor;
    private final JdbcExecutionManager jdbcExecutionManager;
    private final DirectTransactionManager transactionManager;

    public DirectStorageResource(String dataSourceKey, DataSource dataSource) {
        if (dataSourceKey == null || dataSourceKey.isBlank()) throw new IllegalArgumentException("dataSourceKey must not be blank");
        this.dataSourceKey = dataSourceKey.trim();
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.transactionManager = new DirectTransactionManager(dataSource);
        TransactionExecutor delegate = new DefaultTransactionExecutor(new SpringTransactionProvider(transactionManager));
        this.transactionExecutor = new TransactionExecutor() {
            @Override
            public <T> T execute(TransactionOptions options, TransactionCallback<T> callback) {
                // 在 Tx-A 开始前也检查；否则可能先在错误分库提交 PROCESSING，再到 Tx-B 才发现错库。
                assertCompatibleTransaction();
                return delegate.execute(options, callback);
            }
        };
        this.jdbcExecutionManager = new SpringTransactionJdbcExecutionManager(dataSource, transactionExecutor);
    }

    public String dataSourceKey() { return dataSourceKey; }
    public DataSource dataSource() { return dataSource; }
    public TransactionExecutor transactionExecutor() { return transactionExecutor; }
    public JdbcExecutionManager jdbcExecutionManager() { return jdbcExecutionManager; }

    public void assertCompatibleTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive() && !transactionManager.hasBoundTransaction()) {
            throw new IllegalStateException("active transaction belongs to another resource; bind route before transaction: " + dataSourceKey);
        }
    }

    /** hasResource 不够：另一个库的连接可能仅被注册到 synchronization，并不归本地事务管理器管理。 */
    private static final class DirectTransactionManager extends DataSourceTransactionManager {
        private DirectTransactionManager(DataSource dataSource) { super(dataSource); }
        private boolean hasBoundTransaction() { return isExistingTransaction(doGetTransaction()); }
    }
}
