package com.xjtu.iron.idempotent.e2e;

import com.mysql.cj.jdbc.MysqlDataSource;
import com.xjtu.iron.idempotent.api.policy.IdempotencyMode;
import com.xjtu.iron.idempotent.api.policy.IdempotencyWindowPolicy;
import com.xjtu.iron.idempotent.api.recovery.IdempotencyRecoveryMode;
import com.xjtu.iron.idempotent.api.repository.acquire.IdempotencyAcquireRequest;
import com.xjtu.iron.idempotent.api.repository.acquire.IdempotencyAcquireStatus;
import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryQuery;
import com.xjtu.iron.idempotent.api.storage.IdempotencyStorageContext;
import com.xjtu.iron.idempotent.provider.mybatis.repository.RoutedMyBatisIdempotencyRepository;
import com.xjtu.iron.idempotent.provider.mybatis.routing.IdempotencyPhysicalRoute;
import com.xjtu.iron.idempotent.provider.mybatis.routing.IdempotencyPhysicalRouteResolver;
import com.xjtu.iron.relational.mybatis.resource.DirectMyBatisResourceRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 Direct MyBatis Provider 在真实 MySQL 多数据源下只访问目标物理库。 */
@Testcontainers
class DirectStorageRoutingIT {

    /** 测试专用 MySQL 容器。 */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.4");

    /** Direct 注册表使用的两个独立物理数据库。 */
    private static final Map<String, DataSource> DATA_SOURCES = new LinkedHashMap<>();

    /** 逻辑幂等存储上下文。 */
    private static final IdempotencyStorageContext STORAGE = IdempotencyStorageContext.of("order", 0);

    @BeforeAll
    static void createPhysicalDatabases() throws Exception {
        MysqlDataSource admin = dataSource(MYSQL.getDatabaseName());
        JdbcTemplate adminJdbc = new JdbcTemplate(admin);
        String schema = new String(
            Objects.requireNonNull(DirectStorageRoutingIT.class.getResourceAsStream("/META-INF/iron-idempotency/mybatis/schema-mysql.sql"),
                "MySQL idempotency schema must exist").readAllBytes(), StandardCharsets.UTF_8);

        for (String key : List.of("db_00", "db_01")) {
            adminJdbc.execute("CREATE DATABASE " + key);
            DataSource source = dataSource(key);
            executeScript(source, schema);
            DATA_SOURCES.put(key, source);
        }
    }

    @Test
    void shouldAcquireInSelectedDatabaseWithoutWritingTheOtherDatabase() {
        DirectMyBatisResourceRegistry resources = DirectMyBatisResourceRegistry.fromDataSources(DATA_SOURCES);
        RoutedMyBatisIdempotencyRepository repository =
            new RoutedMyBatisIdempotencyRepository(new KeySuffixPhysicalRouteResolver(), resources.getMyBatisAccessResolver());

        assertThat(repository.tryAcquire(acquire("payment-a", "owner-a")).getStatus()).isEqualTo(IdempotencyAcquireStatus.ACQUIRED);
        assertThat(repository.tryAcquire(acquire("payment-b", "owner-b")).getStatus()).isEqualTo(IdempotencyAcquireStatus.ACQUIRED);

        assertThat(count("db_00", "payment-a")).isOne();
        assertThat(count("db_00", "payment-b")).isZero();
        assertThat(count("db_01", "payment-a")).isZero();
        assertThat(count("db_01", "payment-b")).isOne();
    }

    private static IdempotencyAcquireRequest acquire(String key, String ownerToken) {
        return IdempotencyAcquireRequest.builder().storageContext(STORAGE).namespace("payment").key(key).requestHash("hash-" + key)
            .routeKey("merchant-1").ownerToken(ownerToken).mode(IdempotencyMode.DURABLE).processingTimeout(Duration.ofSeconds(30))
            .windowPolicy(IdempotencyWindowPolicy.FIXED_FROM_FIRST_ACQUIRE).recordRetentionTtl(Duration.ZERO)
            .recoveryMode(IdempotencyRecoveryMode.EXTERNAL_TASK).now(Instant.now()).build();
    }

    private static int count(String dataSourceKey, String key) {
        return new JdbcTemplate(DATA_SOURCES.get(dataSourceKey)).queryForObject(
            "SELECT COUNT(*) FROM iron_idempotency_record WHERE idempotency_key = ?", Integer.class, key);
    }

    private static void executeScript(DataSource dataSource, String script) throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            for (String sql : script.split(";")) {
                if (!sql.isBlank()) {
                    statement.execute(sql);
                }
            }
        }
    }

    private static MysqlDataSource dataSource(String database) {
        MysqlDataSource source = new MysqlDataSource();
        source.setURL("jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/" + database +
            "?useSSL=false&allowPublicKeyRetrieval=true");
        source.setUser("root");
        source.setPassword(MYSQL.getPassword());
        return source;
    }

    /** 测试路由：后缀 a 进入 db_00，其余 key 进入 db_01。 */
    private static final class KeySuffixPhysicalRouteResolver implements IdempotencyPhysicalRouteResolver {

        @Override
        public IdempotencyPhysicalRoute resolvePoint(IdempotencyStorageContext storageContext, String namespace, String idempotencyKey) {
            String dataSourceKey = idempotencyKey.endsWith("-a") ? "db_00" : "db_01";
            return IdempotencyPhysicalRoute.of(dataSourceKey, "iron_idempotency_record");
        }

        @Override
        public List<IdempotencyPhysicalRoute> resolveRecoveryRoutes(IdempotencyRecoveryQuery query) {
            return List.of(IdempotencyPhysicalRoute.of("db_00", "iron_idempotency_record"),
                IdempotencyPhysicalRoute.of("db_01", "iron_idempotency_record"));
        }
    }
}
