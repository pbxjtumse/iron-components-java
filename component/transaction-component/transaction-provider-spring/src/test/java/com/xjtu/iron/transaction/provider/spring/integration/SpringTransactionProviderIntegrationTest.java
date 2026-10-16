package com.xjtu.iron.transaction.provider.spring.integration;

import com.xjtu.iron.transaction.api.definition.TransactionOptions;
import com.xjtu.iron.transaction.api.definition.TransactionPropagation;
import com.xjtu.iron.transaction.api.execution.TransactionExecutor;
import com.xjtu.iron.transaction.core.executor.DefaultTransactionExecutor;
import com.xjtu.iron.transaction.provider.spring.transaction.SpringTransactionProvider;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpringTransactionProviderIntegrationTest {

    private JdbcTemplate jdbc;
    private TransactionExecutor executor;

    @BeforeEach
    void setUp() {
        // 1. 测试使用独立 H2 DataSource，不依赖外部数据库环境。
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:tx_provider;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");

        // 2. JdbcTemplate 和 DataSourceTransactionManager 共享同一个 DataSource，才能参加同一 Spring 事务。
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("drop table if exists tx_demo");
        jdbc.execute("create table tx_demo(id int primary key, name varchar(64))");

        // 3. 手工组装完整链路：Executor -> SpringTransactionProvider -> DataSourceTransactionManager。
        executor = new DefaultTransactionExecutor(
                new SpringTransactionProvider(new DataSourceTransactionManager(dataSource)));
    }

    @Test
    void shouldCommitSuccessfulTransaction() {
        // callback 正常结束，当前没有外部事务，因此这次 REQUIRED 最终提交写入。
        executor.executeWithoutResult(ctx -> jdbc.update(
                "insert into tx_demo(id, name) values (?, ?)", 1, "committed"));

        assertEquals(1, count());
    }

    @Test
    void shouldRollbackAndKeepOriginalBusinessException() {
        // 业务异常发生在 INSERT 之后，Provider 应 rollback，并继续抛出原始业务异常。
        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                executor.executeWithoutResult(ctx -> {
                    jdbc.update("insert into tx_demo(id, name) values (?, ?)", 1, "rollback");
                    throw new IllegalStateException("business failed");
                }));

        assertEquals("business failed", failure.getMessage());
        assertEquals(0, count());
    }

    @Test
    void requiresNewShouldSurviveOuterRollback() {
        // 1. 内层显式使用 REQUIRES_NEW，要求创建一个与当前事务独立的 Tx-B。
        TransactionOptions requiresNew = TransactionOptions.builder()
                .name("inner-new")
                .propagation(TransactionPropagation.REQUIRES_NEW)
                .build();

        // 2. 外层默认 REQUIRED 使用 Tx-A；内层 Tx-B 正常提交后，外层再故意失败回滚 Tx-A。
        assertThrows(IllegalStateException.class, () ->
                executor.executeWithoutResult(
                        TransactionOptions.builder().name("outer").build(),
                        outer -> {
                            jdbc.update("insert into tx_demo(id, name) values (?, ?)", 1, "outer");

                            executor.executeWithoutResult(requiresNew, inner ->
                                    jdbc.update("insert into tx_demo(id, name) values (?, ?)", 2, "inner"));

                            throw new IllegalStateException("rollback outer");
                        }));

        // 3. Tx-A 中的数据消失；Tx-B 已独立提交，因此仍然存在。
        assertEquals(1, count());
        assertEquals("inner", jdbc.queryForObject(
                "select name from tx_demo where id = 2", String.class));
    }

    @Test
    void requiredInnerShouldRollbackTogetherWithOuter() {
        // 1. 外层 REQUIRED 在当前无事务时建立 Tx-A。
        assertThrows(IllegalStateException.class, () ->
                executor.executeWithoutResult(
                        TransactionOptions.builder().name("outer").build(),
                        outer -> {
                            jdbc.update("insert into tx_demo(id, name) values (?, ?)", 1, "outer-required");

                            // 2. 内层仍然使用 REQUIRED，因此继续使用当前 Tx-A，而不是创建 Tx-B。
                            executor.executeWithoutResult(
                                    TransactionOptions.builder().name("inner-required").build(),
                                    inner -> jdbc.update(
                                            "insert into tx_demo(id, name) values (?, ?)", 2, "inner-required"));

                            // 3. 外层失败后 Tx-A 整体回滚，两条 INSERT 都应该消失。
                            throw new IllegalStateException("rollback same transaction");
                        }));

        assertEquals(0, count());
    }

    @Test
    void rollbackOnlyShouldRollbackCurrentTransaction() {
        // callback 本身不抛异常，但主动把当前事务标记 rollback-only。
        executor.executeWithoutResult(ctx -> {
            jdbc.update("insert into tx_demo(id, name) values (?, ?)", 1, "rollback-only");
            ctx.setRollbackOnly();
        });

        // Spring 在完成事务时看到 rollback-only，因此最终数据不会提交。
        assertEquals(0, count());
    }

    private int count() {
        // 辅助查询只用于断言最终数据库状态，不参与被测事务。
        Integer count = jdbc.queryForObject("select count(*) from tx_demo", Integer.class);
        return count == null ? 0 : count;
    }
}
