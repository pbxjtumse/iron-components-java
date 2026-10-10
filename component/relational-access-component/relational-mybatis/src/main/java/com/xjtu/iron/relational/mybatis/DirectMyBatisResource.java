package com.xjtu.iron.relational.mybatis;

import com.xjtu.iron.transaction.api.definition.TransactionOptions;
import com.xjtu.iron.transaction.api.execution.TransactionCallback;
import com.xjtu.iron.transaction.api.execution.TransactionExecutor;
import com.xjtu.iron.transaction.core.executor.DefaultTransactionExecutor;
import com.xjtu.iron.transaction.provider.spring.transaction.SpringTransactionProvider;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * 一个 Direct DataSource 对应的 MyBatis 与事务资源集合。
 *
 * <p>资源集合从同一个 DataSource 同时构造 SqlSessionTemplate、TransactionExecutor 和
 * MyBatisAccess，防止“路由选中数据库 A，事务却由数据库 B 管理”的装配错误。</p>
 */
public final class DirectMyBatisResource {

    /** 物理数据源标识。 */
    private final String dataSourceKey;

    /** 调用方拥有的物理 DataSource。 */
    private final DataSource dataSource;

    /** 与当前 DataSource 唯一绑定的事务管理器。 */
    private final DirectTransactionManager transactionManager;

    /** 组件统一事务执行器。 */
    private final TransactionExecutor transactionExecutor;

    /** 当前物理数据库对应的 MyBatis Access。 */
    private final MyBatisAccess myBatisAccess;

    public DirectMyBatisResource(String dataSourceKey, DataSource dataSource) {
        if (dataSourceKey == null || dataSourceKey.isBlank()) {
            throw new IllegalArgumentException("dataSourceKey must not be blank");
        }
        this.dataSourceKey = dataSourceKey.trim();
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.transactionManager = new DirectTransactionManager(dataSource);
        TransactionExecutor delegate = new DefaultTransactionExecutor(
                new SpringTransactionProvider(transactionManager)
        );
        this.transactionExecutor = new TransactionExecutor() {
            @Override
            public <T> T execute(TransactionOptions options, TransactionCallback<T> callback) {
                assertCompatibleTransaction();
                return delegate.execute(options, callback);
            }
        };
        this.myBatisAccess = new SpringMyBatisAccess(
                dataSource,
                createSqlSessionTemplate(dataSource),
                transactionExecutor,
                MyBatisAccessListener.noop()
        );
    }

    public String getDataSourceKey() {
        return dataSourceKey;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public TransactionExecutor getTransactionExecutor() {
        return transactionExecutor;
    }

    public MyBatisAccess getMyBatisAccess() {
        return myBatisAccess;
    }

    /** 外层事务存在时，必须确认它就是当前物理数据源的本地事务。 */
    public void assertCompatibleTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && !transactionManager.hasBoundTransaction()) {
            throw new IllegalStateException(
                    "active transaction belongs to another resource; bind route before transaction: "
                            + dataSourceKey
            );
        }
    }

    private static SqlSessionTemplate createSqlSessionTemplate(DataSource dataSource) {
        try {
            SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
            factoryBean.setDataSource(dataSource);
            factoryBean.setMapperLocations(
                    new PathMatchingResourcePatternResolver().getResources(
                            "classpath*:com/xjtu/iron/**/mapper/*.xml"
                    )
            );
            factoryBean.afterPropertiesSet();
            SqlSessionFactory factory = Objects.requireNonNull(
                    factoryBean.getObject(),
                    "SqlSessionFactoryBean returned null"
            );
            return new SqlSessionTemplate(factory);
        } catch (Exception failure) {
            throw new IllegalStateException(
                    "failed to create MyBatis runtime for a direct DataSource",
                    failure
            );
        }
    }

    /** 暴露真实 existing-transaction 判断，避免把 synchronization-only 误判为本地事务。 */
    private static final class DirectTransactionManager extends DataSourceTransactionManager {

        private DirectTransactionManager(DataSource dataSource) {
            super(dataSource);
        }

        private boolean hasBoundTransaction() {
            return isExistingTransaction(doGetTransaction());
        }
    }
}
