package com.xjtu.iron.relational.mybatis.access;

import com.xjtu.iron.relational.mybatis.observation.MyBatisAccessInvocation;
import com.xjtu.iron.relational.mybatis.observation.MyBatisAccessListener;
import com.xjtu.iron.transaction.core.executor.DefaultTransactionExecutor;
import com.xjtu.iron.transaction.provider.spring.transaction.SpringTransactionProvider;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证资源配对、普通调用事务参与和旁路观测故障隔离。 */
class SpringMyBatisAccessTest {

    private DataSource dataSource;
    private SqlSessionTemplate session;
    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = dataSource();
        new JdbcTemplate(dataSource).execute("CREATE TABLE access_probe (id VARCHAR(64) PRIMARY KEY)");
        session = session(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Test
    void shouldRejectMismatchedDataSourceBeforeMapperExecution() {
        assertThatThrownBy(() -> new SpringMyBatisAccess(dataSource(), session, null, null))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("same DataSource");
    }

    @Test
    void shouldRejectNonSpringTransactionFactory() {
        Configuration configuration = new Configuration(new Environment("jdbc", new JdbcTransactionFactory(), dataSource));
        SqlSessionTemplate jdbcSession = new SqlSessionTemplate(new DefaultSqlSessionFactory(configuration));

        assertThatThrownBy(() -> new SpringMyBatisAccess(dataSource, jdbcSession, null, null))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SpringManagedTransactionFactory");
    }

    @Test
    void shouldParticipateThroughTransactionAwareProxyAndRollback() throws Exception {
        TransactionAwareDataSourceProxy proxy = new TransactionAwareDataSourceProxy(dataSource);
        SpringMyBatisAccess access = new SpringMyBatisAccess(proxy, session(proxy), null, null);
        RuntimeException failure = new IllegalStateException("business failed");

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            insert(access, "proxy");
            throw failure;
        })).isSameAs(failure);

        assertThat(count()).isZero();
    }

    @Test
    void shouldExecuteWithoutTransactionAndCommitWithMatchingTransaction() throws Exception {
        SpringMyBatisAccess access = new SpringMyBatisAccess(dataSource, session, null, null);
        access.execute("probe.auto-commit", ProbeMapper.class, mapper -> mapper.insert("auto-commit"));
        transactions.executeWithoutResult(status -> insert(access, "transaction"));

        assertThat(count()).isEqualTo(2);
    }

    @Test
    void shouldRejectForeignTransactionBeforeWriting() {
        SpringMyBatisAccess access = new SpringMyBatisAccess(dataSource, session, null, null);
        TransactionTemplate other = new TransactionTemplate(new DataSourceTransactionManager(dataSource()));
        AtomicBoolean mapperInvoked = new AtomicBoolean();

        assertThatThrownBy(() -> other.executeWithoutResult(status -> {
            try {
                access.execute("probe.foreign", ProbeMapper.class, mapper -> {
                    mapperInvoked.set(true);
                    return mapper.insert("foreign");
                });
            } catch (RuntimeException failure) {
                throw failure;
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("does not bind");

        assertThat(mapperInvoked).isFalse();
        assertThat(count()).isZero();
    }

    @Test
    void shouldRequireTransactionForExplicitParticipation() {
        SpringMyBatisAccess access = new SpringMyBatisAccess(dataSource, session, null, null);

        assertThatThrownBy(() -> access.executeInCurrentTransaction("probe.mandatory", ProbeMapper.class, ProbeMapper::count))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("active local transaction");
    }

    @Test
    void shouldRejectSynchronizationOnlyConnectionFromAnotherTransaction() {
        SpringMyBatisAccess access = new SpringMyBatisAccess(dataSource, session, null, null);
        TransactionTemplate other = new TransactionTemplate(new DataSourceTransactionManager(dataSource()));

        assertThatThrownBy(() -> other.executeWithoutResult(status -> {
            var connection = DataSourceUtils.getConnection(dataSource);
            try {
                insert(access, "synchronization-only");
            } finally {
                DataSourceUtils.releaseConnection(connection, dataSource);
            }
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("auto-commit");

        assertThat(count()).isZero();
    }

    @Test
    void shouldRejectIndependentExecutorForAnotherResource() {
        var wrongExecutor = new DefaultTransactionExecutor(new SpringTransactionProvider(new DataSourceTransactionManager(dataSource())));
        SpringMyBatisAccess access = new SpringMyBatisAccess(dataSource, session, wrongExecutor, null);

        assertThatThrownBy(() -> access.executeInNewTransaction("probe.wrong-executor", ProbeMapper.class, mapper -> mapper.insert("wrong")))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("does not bind");
        assertThat(count()).isZero();
    }

    @Test
    void listenerFailuresShouldNotRollbackWritesOrBlockOtherListeners() {
        List<String> notifications = new ArrayList<>();
        MyBatisAccessListener recording = new MyBatisAccessListener() {
            @Override
            public void before(MyBatisAccessInvocation invocation) {
                notifications.add("before");
            }

            @Override
            public void afterSuccess(MyBatisAccessInvocation invocation, Duration elapsed) {
                notifications.add("success");
            }
        };
        var listener = MyBatisAccessListener.composite(List.of(throwingListener(), recording));
        SpringMyBatisAccess access = new SpringMyBatisAccess(dataSource, session, null, listener);

        transactions.executeWithoutResult(status -> insert(access, "observed"));

        assertThat(count()).isEqualTo(1);
        assertThat(notifications).containsExactly("before", "success");
    }

    @Test
    void listenerFailureShouldNotHideOriginalMapperFailure() {
        RuntimeException original = new IllegalArgumentException("mapper callback failed");
        List<Throwable> failures = new ArrayList<>();
        MyBatisAccessListener recording = new MyBatisAccessListener() {
            @Override
            public void afterFailure(MyBatisAccessInvocation invocation, Duration elapsed, Throwable failure) {
                failures.add(failure);
            }
        };
        SpringMyBatisAccess access = new SpringMyBatisAccess(dataSource, session, null,
            MyBatisAccessListener.composite(List.of(throwingListener(), recording)));

        assertThatThrownBy(() -> access.execute("probe.failure", ProbeMapper.class, mapper -> { throw original; })).isSameAs(original);
        assertThat(failures).containsExactly(original);
    }

    private static MyBatisAccessListener throwingListener() {
        return new MyBatisAccessListener() {
            @Override
            public void before(MyBatisAccessInvocation invocation) {
                throw new IllegalStateException("before observer failed");
            }

            @Override
            public void afterSuccess(MyBatisAccessInvocation invocation, Duration elapsed) {
                throw new IllegalStateException("success observer failed");
            }

            @Override
            public void afterFailure(MyBatisAccessInvocation invocation, Duration elapsed, Throwable failure) {
                throw new IllegalStateException("failure observer failed");
            }
        };
    }

    private static void insert(MyBatisAccess access, String id) {
        try {
            access.execute("probe.insert", ProbeMapper.class, mapper -> mapper.insert(id));
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private int count() {
        return new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(*) FROM access_probe", Integer.class);
    }

    private static DataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:access_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        return dataSource;
    }

    private static SqlSessionTemplate session(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        Configuration configuration = new Configuration();
        configuration.addMapper(ProbeMapper.class);
        factory.setConfiguration(configuration);
        return new SqlSessionTemplate(factory.getObject());
    }

    public interface ProbeMapper {
        @Insert("INSERT INTO access_probe(id) VALUES (#{id})")
        int insert(@Param("id") String id);

        @Select("SELECT COUNT(*) FROM access_probe")
        int count();
    }
}
