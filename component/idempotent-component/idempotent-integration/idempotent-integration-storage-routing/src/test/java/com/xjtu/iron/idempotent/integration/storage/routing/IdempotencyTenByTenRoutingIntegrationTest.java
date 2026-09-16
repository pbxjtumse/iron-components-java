package com.xjtu.iron.idempotent.integration.storage.routing;

import com.xjtu.iron.idempotent.api.execution.IdempotencyRequest;
import com.xjtu.iron.idempotent.api.execution.IdempotencyResult;
import com.xjtu.iron.idempotent.api.execution.IdempotencyResultStatus;
import com.xjtu.iron.idempotent.api.policy.IdempotencyMode;
import com.xjtu.iron.idempotent.api.policy.IdempotencyPolicy;
import com.xjtu.iron.idempotent.api.recovery.IdempotencyRecoveryPolicy;
import com.xjtu.iron.idempotent.api.repository.IdempotencyRepository;
import com.xjtu.iron.idempotent.api.repository.write.IdempotencyFailureInfo;
import com.xjtu.iron.idempotent.core.execution.DefaultIdempotencyExecutor;
import com.xjtu.iron.idempotent.core.policy.DefaultIdempotencyPolicyRegistry;
import com.xjtu.iron.idempotent.core.repository.DefaultIdempotencyRepositoryRegistry;
import com.xjtu.iron.idempotent.core.state.DefaultIdempotencyStateMachine;
import com.xjtu.iron.idempotent.provider.jdbc.execution.DataSourceJdbcExecutionManager;
import com.xjtu.iron.idempotent.provider.jdbc.execution.JdbcExecutionManager;
import com.xjtu.iron.idempotent.provider.jdbc.execution.RoutingJdbcExecutionManagerResolver;
import com.xjtu.iron.idempotent.provider.jdbc.repository.RoutedJdbcIdempotencyRepository;
import com.xjtu.iron.storage.routing.api.CompositeShardKey;
import com.xjtu.iron.storage.routing.api.ShardKey;
import com.xjtu.iron.storage.routing.api.TableIndexMode;
import com.xjtu.iron.storage.routing.api.mapping.RouteMappingStrategy;
import com.xjtu.iron.storage.routing.api.resolver.StorageRouteResolver;
import com.xjtu.iron.storage.routing.core.context.ThreadLocalStorageRouteContext;
import com.xjtu.iron.storage.routing.core.mapping.GlobalTableIndexRouteMappingStrategy;
import com.xjtu.iron.storage.routing.core.mapping.LocalTableIndexRouteMappingStrategy;
import com.xjtu.iron.storage.routing.core.resolver.HashShardResolver;
import com.xjtu.iron.storage.routing.core.resolver.ShardIdHashStorageRouteResolver;
import com.xjtu.iron.storage.routing.integration.relational.DefaultStorageRouteToSqlRouteBridge;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyTenByTenRoutingIntegrationTest {

    private static final int DATABASE_COUNT = 10;
    private static final int TABLES_PER_DATABASE = 10;
    private static final String TABLE_PREFIX = "iron_idempotency_record";

    @Test
    void shouldRouteAllStateOperationsAcrossTenDatabasesAndTenLocalTables() throws Exception {
        verifyTenByTen(TableIndexMode.LOCAL_TABLE_INDEX);
    }

    @Test
    void shouldRouteAllStateOperationsAcrossTenDatabasesAndOneHundredGlobalTables() throws Exception {
        verifyTenByTen(TableIndexMode.GLOBAL_TABLE_INDEX);
    }

    private void verifyTenByTen(TableIndexMode tableIndexMode) throws Exception {
        Map<String, DataSource> dataSources = createTopology(tableIndexMode);
        ThreadLocalStorageRouteContext routeContext = new ThreadLocalStorageRouteContext();
        ShardIdHashStorageRouteResolver topologyResolver = ShardIdHashStorageRouteResolver.builder()
                .dataSourcePrefix("db_")
                .tablePrefix("business_order")
                .databaseCount(DATABASE_COUNT)
                .tablesPerDatabase(TABLES_PER_DATABASE)
                .dataSourceIndexWidth(2)
                .tableIndexWidth(2)
                .tableIndexMode(tableIndexMode)
                .build();
        AtomicInteger routeResolutions = new AtomicInteger();
        StorageRouteResolver storageRouteResolver = input -> {
            routeResolutions.incrementAndGet();
            return topologyResolver.resolve(input);
        };

        RouteMappingStrategy idempotencyMapping = tableIndexMode == TableIndexMode.GLOBAL_TABLE_INDEX
                ? new GlobalTableIndexRouteMappingStrategy("db_", TABLE_PREFIX, 2, 2)
                : new LocalTableIndexRouteMappingStrategy("db_", TABLE_PREFIX, 2, 2);
        StorageRoutingIdempotencyJdbcRouteResolver jdbcRouteResolver = new StorageRoutingIdempotencyJdbcRouteResolver(
                TABLE_PREFIX, TABLE_PREFIX, storageRouteResolver, routeContext, idempotencyMapping,
                new DefaultStorageRouteToSqlRouteBridge());

        Map<String, JdbcExecutionManager> managers = new LinkedHashMap<>();
        dataSources.forEach((key, dataSource) -> managers.put(key, new DataSourceJdbcExecutionManager(dataSource)));
        IdempotencyRepository repository = new RoutedJdbcIdempotencyRepository(
                jdbcRouteResolver,
                new RoutingJdbcExecutionManagerResolver(null, managers));

        DefaultIdempotencyExecutor core = coreExecutor(repository);
        StorageRouteAwareIdempotencyExecutor executor = new StorageRouteAwareIdempotencyExecutor(
                core,
                new DefaultIdempotencyRouteContextFactory(TABLE_PREFIX),
                storageRouteResolver,
                routeContext);

        HashShardResolver shardResolver = new HashShardResolver(DATABASE_COUNT, TABLES_PER_DATABASE);
        for (int expectedShard = 0; expectedShard < DATABASE_COUNT * TABLES_PER_DATABASE; expectedShard++) {
            int shard = expectedShard;
            String key = keyForShard(shardResolver, shard);
            IdempotencyRequest request = IdempotencyRequest.builder()
                    .key(key)
                    .storeName("ten-by-ten")
                    .policyName("durable")
                    .build();

            IdempotencyResult<String> first = executor.execute(request, context -> "value-" + shard);
            IdempotencyResult<String> replay = executor.execute(request, context -> {
                throw new AssertionError("successful record must be replayed instead of executed again");
            });

            assertThat(first.getStatus()).isEqualTo(IdempotencyResultStatus.EXECUTED);
            assertThat(replay.getStatus()).isEqualTo(IdempotencyResultStatus.REPLAYED);
            assertThat(routeContext.current()).isEmpty();
        }

        for (int database = 0; database < DATABASE_COUNT; database++) {
            DataSource dataSource = dataSources.get(String.format("db_%02d", database));
            for (int table = 0; table < TABLES_PER_DATABASE; table++) {
                int physicalTableIndex = tableIndexMode == TableIndexMode.GLOBAL_TABLE_INDEX ? database * TABLES_PER_DATABASE + table : table;
                assertThat(rowCount(dataSource, String.format("%s_%02d", TABLE_PREFIX, physicalTableIndex)))
                        .as("db_%02d table_%02d", database, table)
                        .isEqualTo(1);
            }
        }
        // 100 个首次执行 + 100 个重复执行；每次 execute 只能计算一次全局 StorageRoute。
        assertThat(routeResolutions).hasValue(DATABASE_COUNT * TABLES_PER_DATABASE * 2);
    }

    private DefaultIdempotencyExecutor coreExecutor(IdempotencyRepository repository) {
        IdempotencyPolicy policy = IdempotencyPolicy.builder()
                .name("durable")
                .mode(IdempotencyMode.DURABLE)
                .repositoryName("jdbc")
                .processingTimeout(Duration.ofSeconds(30))
                .recoveryPolicy(IdempotencyRecoveryPolicy.externalTask())
                .build();
        return new DefaultIdempotencyExecutor(
                new DefaultIdempotencyRepositoryRegistry(List.of(repository), "jdbc", "jdbc"),
                new DefaultIdempotencyPolicyRegistry(List.of(policy), "durable"),
                (namespace, key) -> UUID.randomUUID().toString(),
                (error, at) -> new IdempotencyFailureInfo("BUSINESS_ERROR", error.getMessage(), false, at),
                null,
                null,
                new DefaultIdempotencyStateMachine(),
                null,
                null,
                Clock.fixed(Instant.parse("2026-09-15T00:00:00Z"), ZoneOffset.UTC));
    }

    private Map<String, DataSource> createTopology(TableIndexMode tableIndexMode) throws Exception {
        Map<String, DataSource> result = new LinkedHashMap<>();
        for (int database = 0; database < DATABASE_COUNT; database++) {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:idempotency_route_" + database + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
            dataSource.setUser("sa");
            result.put(String.format("db_%02d", database), dataSource);
            for (int table = 0; table < TABLES_PER_DATABASE; table++) {
                int physicalTableIndex = tableIndexMode == TableIndexMode.GLOBAL_TABLE_INDEX ? database * TABLES_PER_DATABASE + table : table;
                createTable(dataSource, String.format("%s_%02d", TABLE_PREFIX, physicalTableIndex));
            }
        }
        return Map.copyOf(result);
    }

    private void createTable(DataSource dataSource, String table) throws Exception {
        String ddl = "CREATE TABLE " + table + " ("
                + "id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,"
                + "store_name VARCHAR(64) NOT NULL, scan_bucket INT NOT NULL,"
                + "namespace VARCHAR(128) NOT NULL, idempotency_key VARCHAR(256) NOT NULL,"
                + "route_key VARCHAR(256), request_hash VARCHAR(128), status VARCHAR(32) NOT NULL,"
                + "owner_token VARCHAR(128), version BIGINT NOT NULL, result_payload CLOB,"
                + "failure_code VARCHAR(128), failure_message VARCHAR(1024), failure_retryable BOOLEAN NOT NULL,"
                + "recovery_mode VARCHAR(32) NOT NULL, window_policy VARCHAR(64) NOT NULL,"
                + "processing_expire_at TIMESTAMP, window_expire_at TIMESTAMP, retention_expire_at TIMESTAMP,"
                + "created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, completed_at TIMESTAMP,"
                + "UNIQUE(store_name, namespace, idempotency_key))";
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(ddl);
        }
    }

    private String keyForShard(HashShardResolver resolver, int expectedShard) {
        for (int candidate = 0; candidate < 100_000; candidate++) {
            String key = "IDEMP-" + expectedShard + "-" + candidate;
            int actual = resolver.resolve(CompositeShardKey.of(ShardKey.of("idempotency_key", key))).shardId();
            if (actual == expectedShard) {
                return key;
            }
        }
        throw new AssertionError("unable to find key for shard " + expectedShard);
    }

    private long rowCount(DataSource dataSource, String table) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }
}
