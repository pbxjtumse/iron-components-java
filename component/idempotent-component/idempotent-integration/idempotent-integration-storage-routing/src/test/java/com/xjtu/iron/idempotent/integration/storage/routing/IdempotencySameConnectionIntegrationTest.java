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
import com.xjtu.iron.idempotent.integration.transaction.SpringTransactionJdbcExecutionManager;
import com.xjtu.iron.idempotent.integration.transaction.TransactionTemplateIdempotencyTransactionCoordinator;
import com.xjtu.iron.idempotent.provider.jdbc.execution.JdbcExecutionManager;
import com.xjtu.iron.idempotent.provider.jdbc.execution.JdbcWork;
import com.xjtu.iron.idempotent.provider.jdbc.execution.RoutingJdbcExecutionManagerResolver;
import com.xjtu.iron.idempotent.provider.jdbc.repository.RoutedJdbcIdempotencyRepository;
import com.xjtu.iron.storage.routing.api.PhysicalStorageLocation;
import com.xjtu.iron.storage.routing.api.StorageRoute;
import com.xjtu.iron.storage.routing.core.context.ThreadLocalStorageRouteContext;
import com.xjtu.iron.storage.routing.integration.relational.DefaultStorageRouteToSqlRouteBridge;
import com.xjtu.iron.transaction.api.execution.TransactionExecutor;
import com.xjtu.iron.transaction.core.executor.DefaultTransactionExecutor;
import com.xjtu.iron.transaction.provider.spring.transaction.SpringTransactionProvider;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencySameConnectionIntegrationTest {

    @Test
    void businessSqlAndMarkSuccessShouldUseTheSameTransactionBoundConnection() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:idempotency_same_connection;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        createTables(dataSource);

        TransactionExecutor transactionExecutor = new DefaultTransactionExecutor(
                new SpringTransactionProvider(new DataSourceTransactionManager(dataSource)));
        RecordingJdbcExecutionManager manager = new RecordingJdbcExecutionManager(
                new SpringTransactionJdbcExecutionManager(dataSource, transactionExecutor));

        ThreadLocalStorageRouteContext routeContext = new ThreadLocalStorageRouteContext();
        var globalResolver = (com.xjtu.iron.storage.routing.api.resolver.StorageRouteResolver) input ->
                StorageRoute.builder()
                        .context(input)
                        .location(PhysicalStorageLocation.of("db_03", "business_order"))
                        .build();
        StorageRoutingIdempotencyJdbcRouteResolver jdbcRouteResolver =
                new StorageRoutingIdempotencyJdbcRouteResolver(
                        "iron_idempotency_record",
                        "iron_idempotency_record",
                        globalResolver,
                        routeContext,
                        shard -> {
                            throw new AssertionError("direct datasource route must not require shard mapping");
                        },
                        new DefaultStorageRouteToSqlRouteBridge());

        IdempotencyRepository repository = new RoutedJdbcIdempotencyRepository(
                jdbcRouteResolver,
                new RoutingJdbcExecutionManagerResolver(null, Map.of("db_03", manager)));
        DefaultIdempotencyExecutor core = coreExecutor(repository, transactionExecutor);
        StorageRouteAwareIdempotencyExecutor executor = new StorageRouteAwareIdempotencyExecutor(
                core,
                new DefaultIdempotencyRouteContextFactory("iron_idempotency_record"),
                globalResolver,
                routeContext);

        AtomicReference<Connection> businessConnection = new AtomicReference<>();
        IdempotencyResult<String> result = executor.execute(
                IdempotencyRequest.builder().key("ORDER-10001").policyName("durable").build(),
                context -> {
                    Connection connection = DataSourceUtils.getConnection(dataSource);
                    businessConnection.set(connection);
                    try (var statement = connection.prepareStatement(
                            "INSERT INTO business_order(order_id, amount) VALUES (?, ?)")) {
                        statement.setString(1, "ORDER-10001");
                        statement.setInt(2, 100);
                        statement.executeUpdate();
                    } finally {
                        DataSourceUtils.releaseConnection(connection, dataSource);
                    }
                    return "created";
                });

        assertThat(result.getStatus()).isEqualTo(IdempotencyResultStatus.EXECUTED);
        assertThat(result.isTransactionApplied()).isTrue();
        assertThat(manager.currentTransactionConnection()).isSameAs(businessConnection.get());
        assertThat(count(dataSource, "business_order")).isEqualTo(1);
        assertThat(count(dataSource, "iron_idempotency_record")).isEqualTo(1);
        assertThat(routeContext.current()).isEmpty();
    }

    private DefaultIdempotencyExecutor coreExecutor(
            IdempotencyRepository repository,
            TransactionExecutor transactionExecutor
    ) {
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
                new TransactionTemplateIdempotencyTransactionCoordinator(transactionExecutor),
                new DefaultIdempotencyStateMachine(),
                null,
                null,
                Clock.fixed(Instant.parse("2026-09-15T00:00:00Z"), ZoneOffset.UTC));
    }

    private void createTables(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE business_order (order_id VARCHAR(64) PRIMARY KEY, amount INT NOT NULL)");
            statement.execute("CREATE TABLE iron_idempotency_record ("
                    + "id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, store_name VARCHAR(64) NOT NULL,"
                    + "shard_key BIGINT NOT NULL, scan_bucket INT NOT NULL, namespace VARCHAR(128) NOT NULL,"
                    + "idempotency_key VARCHAR(256) NOT NULL, route_key VARCHAR(256), request_hash VARCHAR(128),"
                    + "status VARCHAR(32) NOT NULL, owner_token VARCHAR(128), version BIGINT NOT NULL, result_payload CLOB,"
                    + "failure_code VARCHAR(128), failure_message VARCHAR(1024), failure_retryable BOOLEAN NOT NULL,"
                    + "recovery_mode VARCHAR(32) NOT NULL, window_policy VARCHAR(64) NOT NULL,"
                    + "processing_expire_at TIMESTAMP, window_expire_at TIMESTAMP, retention_expire_at TIMESTAMP,"
                    + "created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, completed_at TIMESTAMP,"
                    + "UNIQUE(store_name, namespace, idempotency_key))");
        }
    }

    private long count(DataSource dataSource, String table) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static final class RecordingJdbcExecutionManager implements JdbcExecutionManager {
        private final JdbcExecutionManager delegate;
        private final AtomicReference<Connection> currentTransactionConnection = new AtomicReference<>();

        private RecordingJdbcExecutionManager(JdbcExecutionManager delegate) {
            this.delegate = delegate;
        }

        @Override
        public <T> T withConnection(JdbcWork<T> work) throws Exception {
            return delegate.withConnection(work);
        }

        @Override
        public <T> T inCurrentTransaction(JdbcWork<T> work) throws Exception {
            return delegate.inCurrentTransaction(connection -> {
                currentTransactionConnection.set(connection);
                return work.execute(connection);
            });
        }

        @Override
        public <T> T inNewTransaction(JdbcWork<T> work) throws Exception {
            return delegate.inNewTransaction(work);
        }

        @Override
        public boolean supportsCurrentTransactionParticipation() {
            return delegate.supportsCurrentTransactionParticipation();
        }

        private Connection currentTransactionConnection() {
            return currentTransactionConnection.get();
        }
    }
}
