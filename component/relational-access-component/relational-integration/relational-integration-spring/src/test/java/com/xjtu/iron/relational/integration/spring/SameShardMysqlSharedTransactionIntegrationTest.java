package com.xjtu.iron.relational.integration.spring;

import com.mysql.cj.jdbc.MysqlDataSource;
import com.xjtu.iron.relational.api.RelationalTemplate;
import com.xjtu.iron.relational.api.statement.SqlRoute;
import com.xjtu.iron.relational.api.statement.SqlStatement;
import com.xjtu.iron.relational.core.DefaultRelationalTemplate;
import com.xjtu.iron.relational.core.connection.RoutingDataSourceResolver;
import com.xjtu.iron.relational.core.exception.StandardSqlExceptionTranslator;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 使用真实 MySQL 验证“同库同事务”。
 *
 * <p>该测试默认不会强制执行。只有配置了 IRON_TEST_MYSQL_* 环境变量后才会跑，避免普通开发环境、
 * CI 环境因为没有真实 MySQL 而失败。</p>
 *
 * <p>业务数据用 JdbcTemplate 模拟 MyBatis，幂等记录用 RelationalTemplate 模拟 JdbcIdempotencyStorage。
 * 如果二者使用同一个目标 DataSource，就可以共享同一个 Spring transaction-bound Connection；
 * 如果幂等记录错落到另一个 DataSource，则不会跟业务库的本地事务一起回滚。</p>
 */
class SameShardMysqlSharedTransactionIntegrationTest {

    private static final String ORDER0_URL = "IRON_TEST_MYSQL_ORDER0_URL";
    private static final String ORDER1_URL = "IRON_TEST_MYSQL_ORDER1_URL";
    private static final String USERNAME = "IRON_TEST_MYSQL_USERNAME";
    private static final String PASSWORD = "IRON_TEST_MYSQL_PASSWORD";

    private static final String ORDER0_ROUTE = "order-db-0";
    private static final String ORDER1_ROUTE = "order-db-1";

    /**
     * 真实 MySQL 集成测试使用和演示库一致的表名，方便在 IDEA Database 面板中直接观察。
     */
    private static final String BUSINESS_TABLE = "business_order";
    private static final String IDEMPOTENCY_TABLE = "iron_idempotency_record";

    /** 测试数据统一前缀，清理时只删除测试数据，不影响其他手工数据。 */
    private static final String TEST_PREFIX = "mysql-it-";

    private MysqlDataSource orderDb0;
    private MysqlDataSource orderDb1;
    private RelationalTemplate relationalTemplate;

    @BeforeAll
    static void assumeMysqlConfigured() {
        Assumptions.assumeTrue(
                hasText(env(ORDER0_URL))
                        && hasText(env(ORDER1_URL))
                        && hasText(env(USERNAME))
                        && hasText(env(PASSWORD)),
                () -> "Real MySQL integration test skipped. Required env vars: "
                        + ORDER0_URL + ", " + ORDER1_URL + ", " + USERNAME + ", " + PASSWORD
        );
    }

    @BeforeEach
    void setUp() {
        orderDb0 = mysqlDataSource(env(ORDER0_URL), env(USERNAME), env(PASSWORD));
        orderDb1 = mysqlDataSource(env(ORDER1_URL), env(USERNAME), env(PASSWORD));

        createTables(orderDb0);
        createTables(orderDb1);
        cleanTables(orderDb0);
        cleanTables(orderDb1);

        RoutingDataSourceResolver resolver = new RoutingDataSourceResolver(
                orderDb0,
                Map.of(
                        ORDER0_ROUTE, orderDb0,
                        ORDER1_ROUTE, orderDb1
                )
        );

        relationalTemplate = new DefaultRelationalTemplate(
                new SpringTransactionAwareConnectionProvider(resolver),
                new StandardSqlExceptionTranslator()
        );
    }

    @AfterEach
    void tearDown() {
        cleanTablesQuietly(orderDb0);
        cleanTablesQuietly(orderDb1);
    }

    @Test
    void businessAndIdempotencyRecordShouldCommitTogetherOnRealMysql() {
        TransactionTemplate transactionTemplate = transactionTemplate(orderDb1);
        JdbcTemplate businessJdbcTemplate = new JdbcTemplate(orderDb1);
        String orderId = nextId("commit-order");
        String idempotencyKey = nextId("commit-idem");

        transactionTemplate.executeWithoutResult(status -> {
            businessJdbcTemplate.update(
                    "INSERT INTO " + BUSINESS_TABLE + "(order_id, amount) VALUES (?, ?)",
                    orderId,
                    100
            );

            relationalTemplate.update(SqlStatement.of(
                    "mysql.idempotency.insert-success",
                    "INSERT INTO " + IDEMPOTENCY_TABLE + "(idempotency_key, biz_id, status) VALUES (?, ?, ?)",
                    idempotencyKey,
                    orderId,
                    "SUCCESS"
            ).withRoute(SqlRoute.of(ORDER1_ROUTE)));
        });

        assertThat(countBusiness(orderDb1, orderId)).isEqualTo(1L);
        assertThat(countIdempotency(orderDb1, idempotencyKey)).isEqualTo(1L);
        assertThat(countBusiness(orderDb0, orderId)).isZero();
        assertThat(countIdempotency(orderDb0, idempotencyKey)).isZero();
    }

    @Test
    void businessAndIdempotencyRecordShouldRollbackTogetherOnRealMysql() {
        TransactionTemplate transactionTemplate = transactionTemplate(orderDb1);
        JdbcTemplate businessJdbcTemplate = new JdbcTemplate(orderDb1);
        String orderId = nextId("rollback-order");
        String idempotencyKey = nextId("rollback-idem");

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            businessJdbcTemplate.update(
                    "INSERT INTO " + BUSINESS_TABLE + "(order_id, amount) VALUES (?, ?)",
                    orderId,
                    200
            );

            relationalTemplate.update(SqlStatement.of(
                    "mysql.idempotency.insert-processing",
                    "INSERT INTO " + IDEMPOTENCY_TABLE + "(idempotency_key, biz_id, status) VALUES (?, ?, ?)",
                    idempotencyKey,
                    orderId,
                    "PROCESSING"
            ).withRoute(SqlRoute.of(ORDER1_ROUTE)));

            throw new IllegalStateException("force real mysql rollback");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("force real mysql rollback");

        assertThat(countBusiness(orderDb1, orderId)).isZero();
        assertThat(countIdempotency(orderDb1, idempotencyKey)).isZero();
        assertThat(countBusiness(orderDb0, orderId)).isZero();
        assertThat(countIdempotency(orderDb0, idempotencyKey)).isZero();
    }

    @Test
    void wrongIdempotencyShardShouldNotRollbackWithBusinessShardOnRealMysql() {
        TransactionTemplate transactionTemplate = transactionTemplate(orderDb1);
        JdbcTemplate businessJdbcTemplate = new JdbcTemplate(orderDb1);
        String orderId = nextId("wrong-route-order");
        String idempotencyKey = nextId("wrong-route-idem");

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            businessJdbcTemplate.update(
                    "INSERT INTO " + BUSINESS_TABLE + "(order_id, amount) VALUES (?, ?)",
                    orderId,
                    300
            );

            relationalTemplate.update(SqlStatement.of(
                    "mysql.idempotency.insert-wrong-shard",
                    "INSERT INTO " + IDEMPOTENCY_TABLE + "(idempotency_key, biz_id, status) VALUES (?, ?, ?)",
                    idempotencyKey,
                    orderId,
                    "PROCESSING"
            ).withRoute(SqlRoute.of(ORDER0_ROUTE)));

            throw new IllegalStateException("force rollback after real mysql wrong route");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("force rollback after real mysql wrong route");

        assertThat(countBusiness(orderDb1, orderId)).isZero();
        assertThat(countIdempotency(orderDb1, idempotencyKey)).isZero();

        // 这个断言证明：幂等记录错落到另一个真实 MySQL 库时，不会跟业务库本地事务一起回滚。
        assertThat(countIdempotency(orderDb0, idempotencyKey)).isEqualTo(1L);
    }

    private static TransactionTemplate transactionTemplate(DataSource dataSource) {
        return new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    private static MysqlDataSource mysqlDataSource(String url, String username, String password) {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setURL(url);
        dataSource.setUser(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    private static void createTables(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS business_order (
                    order_id VARCHAR(64) PRIMARY KEY,
                    amount DECIMAL(18, 2) NOT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS iron_idempotency_record (
                    idempotency_key VARCHAR(128) PRIMARY KEY,
                    biz_id VARCHAR(64) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }

    private static void cleanTables(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("DELETE FROM " + IDEMPOTENCY_TABLE + " WHERE idempotency_key LIKE ?", TEST_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM " + BUSINESS_TABLE + " WHERE order_id LIKE ?", TEST_PREFIX + "%");
    }

    private static void cleanTablesQuietly(DataSource dataSource) {
        if (dataSource == null) {
            return;
        }
        try {
            cleanTables(dataSource);
        } catch (RuntimeException ignored) {
            // Cleanup must not hide the real test failure.
        }
    }

    private static long countBusiness(DataSource dataSource, String orderId) {
        Long count = new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM " + BUSINESS_TABLE + " WHERE order_id = ?",
                Long.class,
                orderId
        );
        return count == null ? 0L : count;
    }

    private static long countIdempotency(DataSource dataSource, String idempotencyKey) {
        Long count = new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM " + IDEMPOTENCY_TABLE + " WHERE idempotency_key = ?",
                Long.class,
                idempotencyKey
        );
        return count == null ? 0L : count;
    }

    private static String nextId(String scene) {
        return TEST_PREFIX + scene + "-" + UUID.randomUUID().toString().replace("-", "");
    }

    private static String env(String name) {
        return System.getenv(name);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
