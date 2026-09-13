package com.xjtu.iron.relational.integration.spring;

import com.xjtu.iron.relational.api.RelationalTemplate;
import com.xjtu.iron.relational.api.statement.SqlRoute;
import com.xjtu.iron.relational.api.statement.SqlStatement;
import com.xjtu.iron.relational.core.DefaultRelationalTemplate;
import com.xjtu.iron.relational.core.connection.RoutingDataSourceResolver;
import com.xjtu.iron.relational.core.exception.StandardSqlExceptionTranslator;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 打穿“同库同事务”的关键验证。
 *
 * <p>本测试用两个 H2 DataSource 模拟两个业务分片库。业务数据用 Spring JdbcTemplate 模拟 MyBatis，
 * 幂等记录用 RelationalTemplate 模拟 JdbcIdempotencyStorage。只要二者落到同一个目标 DataSource，
 * SpringTransactionAwareConnectionProvider 就会通过 DataSourceUtils 复用同一个 transaction-bound Connection，
 * 从而实现本地事务内一起提交、一起回滚。</p>
 */
class SameShardSharedTransactionIntegrationTest {

    private JdbcDataSource orderDb0;
    private JdbcDataSource orderDb1;
    private RelationalTemplate relationalTemplate;

    @BeforeEach
    void setUp() throws Exception {
        orderDb0 = dataSource("order_db_0");
        orderDb1 = dataSource("order_db_1");

        createTables(orderDb0);
        createTables(orderDb1);

        RoutingDataSourceResolver resolver = new RoutingDataSourceResolver(
                orderDb0,
                Map.of(
                        "order-db-0", orderDb0,
                        "order-db-1", orderDb1
                )
        );

        relationalTemplate = new DefaultRelationalTemplate(
                new SpringTransactionAwareConnectionProvider(resolver),
                new StandardSqlExceptionTranslator()
        );
    }

    @Test
    void businessAndIdempotencyRecordShouldCommitTogetherInTheSameShard() {
        TransactionTemplate transactionTemplate = transactionTemplate(orderDb1);
        JdbcTemplate businessJdbcTemplate = new JdbcTemplate(orderDb1);

        transactionTemplate.executeWithoutResult(status -> {
            // 模拟业务 MyBatis：业务订单落到 order-db-1。
            businessJdbcTemplate.update(
                    "INSERT INTO business_order(order_id, amount) VALUES (?, ?)",
                    "order-commit-1",
                    100
            );

            // 模拟 JdbcIdempotencyStorage：幂等记录也使用同一个 dataSourceKey = order-db-1。
            relationalTemplate.update(SqlStatement.of(
                    "idempotency.insert-processing",
                    "INSERT INTO iron_idempotency_record(idempotency_key, status) VALUES (?, ?)",
                    "idem-commit-1",
                    "SUCCESS"
            ).withRoute(SqlRoute.of("order-db-1")));
        });

        assertThat(count(orderDb1, "business_order")).isEqualTo(1L);
        assertThat(count(orderDb1, "iron_idempotency_record")).isEqualTo(1L);
        assertThat(count(orderDb0, "business_order")).isZero();
        assertThat(count(orderDb0, "iron_idempotency_record")).isZero();
    }

    @Test
    void businessAndIdempotencyRecordShouldRollbackTogetherInTheSameShard() {
        TransactionTemplate transactionTemplate = transactionTemplate(orderDb1);
        JdbcTemplate businessJdbcTemplate = new JdbcTemplate(orderDb1);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            // 模拟业务 MyBatis：业务订单落到 order-db-1。
            businessJdbcTemplate.update(
                    "INSERT INTO business_order(order_id, amount) VALUES (?, ?)",
                    "order-rollback-1",
                    200
            );

            // 模拟 JdbcIdempotencyStorage：幂等记录也落到 order-db-1。
            relationalTemplate.update(SqlStatement.of(
                    "idempotency.insert-processing",
                    "INSERT INTO iron_idempotency_record(idempotency_key, status) VALUES (?, ?)",
                    "idem-rollback-1",
                    "PROCESSING"
            ).withRoute(SqlRoute.of("order-db-1")));

            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("force rollback");

        assertThat(count(orderDb1, "business_order")).isZero();
        assertThat(count(orderDb1, "iron_idempotency_record")).isZero();
        assertThat(count(orderDb0, "business_order")).isZero();
        assertThat(count(orderDb0, "iron_idempotency_record")).isZero();
    }

    @Test
    void idempotencyRouteMustMatchBusinessTransactionShard() {
        TransactionTemplate transactionTemplate = transactionTemplate(orderDb1);
        JdbcTemplate businessJdbcTemplate = new JdbcTemplate(orderDb1);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            // 当前事务由 order-db-1 的 TransactionManager 开启，业务数据在 order-db-1。
            businessJdbcTemplate.update(
                    "INSERT INTO business_order(order_id, amount) VALUES (?, ?)",
                    "order-wrong-route-1",
                    300
            );

            // 故意写错：幂等记录路由到 order-db-0。
            // 这不是同一个本地事务资源，业务回滚并不代表另一个库里的幂等记录也能跟着回滚。
            relationalTemplate.update(SqlStatement.of(
                    "idempotency.insert-wrong-shard",
                    "INSERT INTO iron_idempotency_record(idempotency_key, status) VALUES (?, ?)",
                    "idem-wrong-route-1",
                    "PROCESSING"
            ).withRoute(SqlRoute.of("order-db-0")));

            throw new IllegalStateException("force rollback after wrong route");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("force rollback after wrong route");

        assertThat(count(orderDb1, "business_order")).isZero();
        assertThat(count(orderDb1, "iron_idempotency_record")).isZero();

        // 这个断言非常重要：它证明“同库同事务”不是口号，而是强约束。
        // 如果幂等记录落到另一个库，它不会自动跟业务库的本地事务一起回滚。
        assertThat(count(orderDb0, "iron_idempotency_record")).isEqualTo(1L);
    }

    private static TransactionTemplate transactionTemplate(DataSource dataSource) {
        return new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    private static long count(DataSource dataSource, String tableName) {
        Long count = new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM " + tableName,
                Long.class
        );
        return count == null ? 0L : count;
    }

    private static JdbcDataSource dataSource(String name) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:relational_same_shard_" + name + "_"
                + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private static void createTables(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE business_order (
                        order_id VARCHAR(64) PRIMARY KEY,
                        amount DECIMAL(18, 2) NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE iron_idempotency_record (
                        idempotency_key VARCHAR(128) PRIMARY KEY,
                        status VARCHAR(32) NOT NULL
                    )
                    """);
        }
    }
}
