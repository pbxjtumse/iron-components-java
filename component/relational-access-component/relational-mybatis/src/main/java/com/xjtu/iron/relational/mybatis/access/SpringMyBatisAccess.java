package com.xjtu.iron.relational.mybatis.access;

import com.xjtu.iron.relational.mybatis.observation.MyBatisAccessInvocation;
import com.xjtu.iron.relational.mybatis.observation.MyBatisAccessListener;
import com.xjtu.iron.transaction.api.definition.TransactionOptions;
import com.xjtu.iron.transaction.api.definition.TransactionPropagation;
import com.xjtu.iron.transaction.api.execution.TransactionExecutor;
import org.apache.ibatis.mapping.Environment;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** 基于 SqlSessionTemplate 和 transaction-component 的 MyBatis Access。
 * 1 SpringMyBatisAccess
 *     =
 * 1 DataSource
 *     +
 * 1 SqlSessionTemplate
 *     +
 * 0/1 TransactionExecutor
 *     +
 * 1 Listener
 */
public final class SpringMyBatisAccess implements MyBatisAccess {

    /** 当前 MyBatis 运行时使用的数据源。 */
    private final DataSource dataSource;

    /** Spring 管理、线程安全的 MyBatis 会话模板。 */
    private final SqlSessionTemplate sqlSessionTemplate;

    /** 可选事务执行器；存在时提供当前事务校验和 REQUIRES_NEW。 */
    private final TransactionExecutor transactionExecutor;

    /** 统一访问观测监听器。 */
    private final MyBatisAccessListener listener;

    public SpringMyBatisAccess(
            DataSource dataSource,
            SqlSessionTemplate sqlSessionTemplate,
            TransactionExecutor transactionExecutor,
            MyBatisAccessListener listener
    ) {
        this.dataSource = transactionResource(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.sqlSessionTemplate = Objects.requireNonNull(sqlSessionTemplate, "sqlSessionTemplate must not be null");
        Environment environment = sqlSessionTemplate.getSqlSessionFactory().getConfiguration().getEnvironment();
        if (environment == null || environment.getDataSource() == null ||
            transactionResource(environment.getDataSource()) != this.dataSource) {
            throw new IllegalArgumentException("SqlSessionTemplate must use the same DataSource as MyBatisAccess");
        }
        if (!(environment.getTransactionFactory() instanceof SpringManagedTransactionFactory)) {
            throw new IllegalArgumentException("MyBatisAccess requires SpringManagedTransactionFactory");
        }
        this.transactionExecutor = transactionExecutor;
        this.listener = MyBatisAccessListener.composite(listener == null ? List.of() : List.of(listener));
    }

    @Override
    public <M, T> T execute(String operationName, Class<M> mapperType, MyBatisMapperWork<M, T> work) throws Exception {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            requireCompatibleCurrentTransaction();
        }
        return invoke(operationName, mapperType, work);
    }

    @Override
    public <M, T> T executeInCurrentTransaction(
            String operationName,
            Class<M> mapperType,
            MyBatisMapperWork<M, T> work
    ) throws Exception {
        requireCompatibleCurrentTransaction();
        return invoke(operationName, mapperType, work);
    }

    @Override
    public <M, T> T executeInNewTransaction(
            String operationName,
            Class<M> mapperType,
            MyBatisMapperWork<M, T> work
    ) throws Exception {
        if (transactionExecutor == null) {
            throw new IllegalStateException(
                    "REQUIRES_NEW MyBatis access requires transaction-component TransactionExecutor"
            );
        }
        TransactionOptions options = TransactionOptions.builder()
                .name(operationName)
                .propagation(TransactionPropagation.REQUIRES_NEW)
                .build();
        try {
            return transactionExecutor.execute(options, context -> {
                try {
                    requireCompatibleCurrentTransaction();
                    return invoke(operationName, mapperType, work);
                } catch (RuntimeException | Error unchecked) {
                    throw unchecked;
                } catch (Exception checked) {
                    throw new CheckedMapperWorkRuntimeException(checked);
                }
            });
        } catch (CheckedMapperWorkRuntimeException checked) {
            throw checked.original;
        }
    }

    @Override
    public boolean supportsCurrentTransactionParticipation() {
        // SqlSessionTemplate 会复用 Spring 绑定到当前线程的连接；该能力不依赖本类是否持有
        // TransactionExecutor。TransactionExecutor 只用于主动开启 REQUIRES_NEW。
        return true;
    }

    /**
     *
     * 创建调用信息
     *     ↓
     * 发送 before 观测
     *     ↓
     * 从 SqlSessionTemplate 获取 Mapper
     *     ↓
     * 执行 Repository 传入的回调
     *     ↓
     * 发送 success/failure 观测
     */
    private <M, T> T invoke(String operationName, Class<M> mapperType, MyBatisMapperWork<M, T> work) throws Exception {
        Objects.requireNonNull(mapperType, "mapperType must not be null");
        Objects.requireNonNull(work, "work must not be null");
        MyBatisAccessInvocation invocation = new MyBatisAccessInvocation(operationName, mapperType);
        long startedNanos = System.nanoTime();
        listener.before(invocation);
        try {
            T result = work.execute(sqlSessionTemplate.getMapper(mapperType));
            listener.afterSuccess(invocation, elapsed(startedNanos));
            return result;
        } catch (Exception | Error failure) {
            listener.afterFailure(invocation, elapsed(startedNanos), failure);
            throw failure;
        }
    }

    private void requireCompatibleCurrentTransaction() throws SQLException {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("MyBatis operation requires an active local transaction");
        }
        if (!TransactionSynchronizationManager.hasResource(dataSource)) {
            throw new IllegalStateException("current transaction does not bind the MyBatis DataSource");
        }
        java.sql.Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            if (!DataSourceUtils.isConnectionTransactional(connection, dataSource)) {
                throw new IllegalStateException("current transaction does not own the MyBatis DataSource connection");
            }
            if (connection.getAutoCommit()) {
                throw new IllegalStateException("MyBatis DataSource connection is in auto-commit mode, not an active local transaction");
            }
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private Duration elapsed(long startedNanos) {
        return Duration.ofNanos(Math.max(0L, System.nanoTime() - startedNanos));
    }

    /** Spring 事务按代理的目标数据源绑定；其他代理必须保留其资源身份。 */
    private static DataSource transactionResource(DataSource dataSource) {
        DataSource current = dataSource;
        while (current instanceof TransactionAwareDataSourceProxy proxy) {
            current = Objects.requireNonNull(proxy.getTargetDataSource(), "transaction-aware proxy must have a target DataSource");
        }
        return current;
    }

    /** 只用于跨过 TransactionExecutor callback 的 checked exception 边界。 */
    private static final class CheckedMapperWorkRuntimeException extends RuntimeException {

        /** 原始 checked exception。 */
        private final Exception original;

        private CheckedMapperWorkRuntimeException(Exception original) {
            super(original);
            this.original = original;
        }
    }
}
