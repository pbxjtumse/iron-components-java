package com.xjtu.iron.idempotent.e2e;

import com.mysql.cj.jdbc.MysqlDataSource;
import com.xjtu.iron.idempotent.api.execution.*;
import com.xjtu.iron.idempotent.api.policy.*;
import com.xjtu.iron.idempotent.api.recovery.*;
import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryQuery;
import com.xjtu.iron.idempotent.api.repository.write.IdempotencyFailureInfo;
import com.xjtu.iron.idempotent.api.repository.write.IdempotencyDiscardRequest;
import com.xjtu.iron.idempotent.api.repository.write.IdempotencyWriteStatus;
import com.xjtu.iron.idempotent.api.result.*;
import com.xjtu.iron.idempotent.core.execution.DefaultIdempotencyExecutor;
import com.xjtu.iron.idempotent.core.policy.DefaultIdempotencyPolicyRegistry;
import com.xjtu.iron.idempotent.core.repository.DefaultIdempotencyRepositoryRegistry;
import com.xjtu.iron.idempotent.integration.storage.routing.*;
import com.xjtu.iron.idempotent.integration.transaction.DirectStorageResourceRegistry;
import com.xjtu.iron.idempotent.provider.jdbc.execution.*;
import com.xjtu.iron.idempotent.provider.jdbc.repository.RoutedJdbcIdempotencyRepository;
import com.xjtu.iron.relational.api.RelationalTemplate;
import com.xjtu.iron.relational.api.statement.SqlStatement;
import com.xjtu.iron.relational.core.DefaultRelationalTemplate;
import com.xjtu.iron.relational.core.exception.StandardSqlExceptionTranslator;
import com.xjtu.iron.relational.integration.spring.SpringTransactionAwareConnectionProvider;
import com.xjtu.iron.storage.routing.api.*;
import com.xjtu.iron.storage.routing.api.mapping.RouteMappingStrategy;
import com.xjtu.iron.storage.routing.api.resolver.StorageRouteResolver;
import com.xjtu.iron.storage.routing.core.context.ThreadLocalStorageRouteContext;
import com.xjtu.iron.storage.routing.core.mapping.RouteMappingStrategyFactory;
import com.xjtu.iron.storage.routing.core.resolver.DefaultStorageRouteResolver;
import com.xjtu.iron.storage.routing.core.resolver.HashShardResolver;
import com.xjtu.iron.storage.routing.integration.relational.DefaultStorageRouteToSqlRouteBridge;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

/** 真实 MySQL + 10 个独立 DataSource；不以 H2 或模拟 Repository 替代状态 SQL。 */
@Testcontainers
class DirectStorageRoutingIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.4");
    private static final Map<String, DataSource> SOURCES = new LinkedHashMap<>();

    @BeforeAll
    static void createIsolatedTopology() throws Exception {
        // 只使用本测试创建的临时容器；不接收生产 URL，不修改外部数据库。
        MysqlDataSource admin = dataSource(MYSQL.getDatabaseName());
        String ddl;
        try (var input = Objects.requireNonNull(DirectStorageRoutingIT.class.getResourceAsStream("/META-INF/iron-idempotency/jdbc/schema-mysql.sql"))) {
            ddl = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (int db = 0; db < 10; db++) {
            String key = String.format(Locale.ROOT, "db_%02d", db);
            new JdbcTemplate(admin).execute("CREATE DATABASE " + key);
            SOURCES.put(key, dataSource(key));
        }
        for (TableIndexMode mode : TableIndexMode.values()) {
            var business = mapping(mode, "business_" + mode.name().toLowerCase(Locale.ROOT));
            var idempotency = mapping(mode, "idempotency_" + mode.name().toLowerCase(Locale.ROOT));
            for (int shard = 0; shard < 100; shard++) {
                var info = shard(shard);
                var jdbc = new JdbcTemplate(SOURCES.get(business.map(info).dataSourceKey()));
                jdbc.execute("CREATE TABLE " + business.map(info).tableName() + " (order_id VARCHAR(100) PRIMARY KEY, amount INT NOT NULL) ENGINE=InnoDB");
                jdbc.execute(ddl.replace("iron_idempotency_record", idempotency.map(info).tableName()));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(TableIndexMode.class)
    void boundBusinessRouteCoversAll100ShardsWithoutRehash(TableIndexMode mode) {
        Fixture f = new Fixture(mode);
        for (int shard = 0; shard < 100; shard++) {
            String key = "bound-" + mode + "-" + shard;
            StorageRoute route = f.businessRoute(shard);
            try (var scope = f.routes.open(route)) {
                assertThat(f.executor.execute(request(key), c -> f.insertOrder(key)).getStatus()).isEqualTo(IdempotencyResultStatus.EXECUTED);
                f.assertSuccessfulTrace();
                assertThat(f.executor.execute(request(key), c -> { throw new AssertionError("duplicate callback"); }).getStatus()).isEqualTo(IdempotencyResultStatus.REPLAYED);
                assertThat(f.routes.requireCurrent()).isSameAs(route);
            }
            f.assertOnlyTargetContains(key, route, "SUCCESS", 1);
            f.trace.clear();
        }
        assertThat(f.resolutions).hasValue(0);
        assertThat(f.routes.current()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(TableIndexMode.class)
    void defaultKeyRouteCoversAll100ShardsAndResolvesOncePerInvocation(TableIndexMode mode) {
        Fixture f = new Fixture(mode);
        Map<Integer, String> keys = new HashMap<>();
        for (int i = 0; i < 100000 && keys.size() < 100; i++) {
            String key = "default-" + mode + "-" + i;
            keys.putIfAbsent(f.hash.resolve(CompositeShardKey.of(ShardKey.of("idempotencyKey", key))).shardId(), key);
        }
        assertThat(keys).hasSize(100);
        for (var entry : keys.entrySet()) {
            String key = entry.getValue();
            int before = f.resolutions.get();
            assertThat(f.executor.execute(request(key), c -> f.insertOrder(key)).getStatus()).isEqualTo(IdempotencyResultStatus.EXECUTED);
            assertThat(f.resolutions).hasValue(before + 1);
            f.assertSuccessfulTrace();
            assertThat(f.executor.execute(request(key), c -> { throw new AssertionError("duplicate callback"); }).getStatus()).isEqualTo(IdempotencyResultStatus.REPLAYED);
            assertThat(f.resolutions).hasValue(before + 2);
            f.assertOnlyTargetContains(key, f.businessRoute(entry.getKey()), "SUCCESS", 1);
            assertThat(f.routes.current()).isEmpty();
            f.trace.clear();
        }
    }

    @Test
    void businessFailureRollsBackTxBAndTxCPersistsFailedThenRecoveryUsesSameShard() {
        Fixture f = new Fixture(TableIndexMode.LOCAL_TABLE_INDEX);
        String key = "recover-" + UUID.randomUUID();
        var route = f.businessRoute(37);
        try (var scope = f.routes.open(route)) {
            assertThat(f.executor.execute(request(key), c -> { f.insertOrder(key); throw new IllegalStateException("business failed"); }).getStatus())
                    .isEqualTo(IdempotencyResultStatus.EXECUTION_FAILED);
            assertThat(f.trace).extracting(Trace::phase).containsExactly("NEW", "BUSINESS", "NEW");
            assertThat(f.trace.get(0).connection()).isNotSameAs(f.trace.get(1).connection()).isNotSameAs(f.trace.get(2).connection());
            assertThat(f.trace.get(1).connection()).isNotSameAs(f.trace.get(2).connection());
            f.assertOnlyTargetContains(key, route, "FAILED", 0);
            var query = new IdempotencyRecoveryQuery("default", "default", 0, Instant.now(), 100);
            var candidate = f.repository.findRecoveryCandidates(query).stream().filter(c -> key.equals(c.getKey())).findFirst().orElseThrow();
            var stale = IdempotencyRecoveryRequest.builder().key(key).requestHash("hash-" + key).routeKey("merchant-42")
                    .expectedOwnerToken("stale").expectedVersion(candidate.getVersion()).build();
            assertThat(f.executor.recover(stale, c -> { throw new AssertionError("stale callback"); }).getStatus())
                    .isEqualTo(IdempotencyResultStatus.STALE_RECOVERY_CANDIDATE);
            var recovery = IdempotencyRecoveryRequest.builder().key(key).requestHash(candidate.getRequestHash()).routeKey(candidate.getRouteKey())
                    .expectedOwnerToken(candidate.getOwnerToken()).expectedVersion(candidate.getVersion()).build();
            f.trace.clear();
            assertThat(f.executor.recover(recovery, c -> f.insertOrder(key)).getStatus()).isEqualTo(IdempotencyResultStatus.RECOVERED);
            f.assertSuccessfulTrace();
            f.assertOnlyTargetContains(key, route, "SUCCESS", 1);
        }
        assertThat(f.routes.current()).isEmpty();
        assertThatThrownBy(() -> f.repository.findRecoveryCandidates(new IdempotencyRecoveryQuery("default", "default", 0, Instant.now(), 10)))
                .isInstanceOf(StorageRoutingException.class);
    }

    @Test
    void joinsExistingSameResourceTransactionAndOuterRollbackUndoesBusinessAndSuccess() {
        Fixture f = new Fixture(TableIndexMode.LOCAL_TABLE_INDEX);
        String key = "outer-" + UUID.randomUUID();
        StorageRoute route = f.businessRoute(83);
        try (var scope = f.routes.open(route)) {
            assertThatThrownBy(() -> f.resources.require("db_08").transactionExecutor().execute(c -> {
                assertThat(f.executor.execute(request(key), ctx -> f.insertOrder(key)).getStatus()).isEqualTo(IdempotencyResultStatus.EXECUTED);
                f.assertSuccessfulTrace();
                throw new IllegalStateException("outer rollback");
            })).isInstanceOf(IllegalStateException.class).hasMessage("outer rollback");
        }
        // Tx-A 已独立提交；外层在 executor 返回后回滚，状态仍为 PROCESSING，不伪造 FAILED。
        f.assertOnlyTargetContains(key, route, "PROCESSING", 0);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(f.routes.current()).isEmpty();
    }

    @Test
    void wrongOuterResourceFailsBeforeAcquiringState() {
        Fixture f = new Fixture(TableIndexMode.LOCAL_TABLE_INDEX);
        String key = "wrong-" + UUID.randomUUID();
        var route = f.businessRoute(37);
        f.resources.require("db_00").transactionExecutor().execute(c -> {
            try (var scope = f.routes.open(route)) {
                assertThat(f.executor.execute(request(key), ctx -> { throw new AssertionError("wrong resource callback"); }).getStatus())
                        .isEqualTo(IdempotencyResultStatus.REPOSITORY_ERROR);
            }
            return null;
        });
        assertThat(f.trace).isEmpty();
        f.assertOnlyTargetContains(key, route, null, 0);
    }

    @Test
    void staleCompletionRollsBackBusinessAndDiscardUsesTheBoundShard() {
        Fixture f = new Fixture(TableIndexMode.LOCAL_TABLE_INDEX);
        String key = "stale-owner-" + UUID.randomUUID();
        var route = f.businessRoute(25);
        var request = request(key);
        try (var scope = f.routes.open(route)) {
            var result = f.executor.execute(request, c -> {
                f.insertOrder(key);
                // 模拟外部恢复者在旧 owner 完成前推进 generation；这里故意用独立连接提交。
                new JdbcTemplate(SOURCES.get(route.dataSourceKey())).update("UPDATE " + f.idempotencyMapping.map(route.shardInfo()).tableName()
                        + " SET version=version+1 WHERE idempotency_key=?", key);
                return key;
            });
            assertThat(result.getStatus()).isEqualTo(IdempotencyResultStatus.OWNERSHIP_LOST);
            f.assertOnlyTargetContains(key, route, "PROCESSING", 0);
            var record = f.repository.find(request.storageContext(), "default", key).orElseThrow();
            f.resources.require(route.dataSourceKey()).transactionExecutor().execute(c -> {
                var discard = new IdempotencyDiscardRequest(request.storageContext(), "default", key, record.getOwnerToken(), record.getVersion(), null,
                        IdempotencyMode.DURABLE, null, IdempotencyWindowPolicy.FIXED_FROM_FIRST_ACQUIRE, java.time.Duration.ZERO, Instant.now());
                assertThat(f.repository.markDiscarded(discard).getStatus()).isEqualTo(IdempotencyWriteStatus.UPDATED);
                return null;
            });
            f.assertOnlyTargetContains(key, route, "DISCARDED", 0);
            assertThat(f.executor.execute(request, c -> { throw new AssertionError("discarded callback"); }).getStatus())
                    .isEqualTo(IdempotencyResultStatus.PREVIOUS_DISCARDED);
        }
        assertThat(f.routes.current()).isEmpty();
    }

    @Test
    void conflictsAndCaptureFailureNeverLeakBusinessCommit() {
        Fixture f = new Fixture(TableIndexMode.GLOBAL_TABLE_INDEX);
        String key = "conflict-" + UUID.randomUUID();
        var route = f.businessRoute(69);
        try (var scope = f.routes.open(route)) {
            assertThat(f.executor.execute(request(key), c -> f.insertOrder(key)).getStatus()).isEqualTo(IdempotencyResultStatus.EXECUTED);
            var conflict = IdempotencyRequest.builder().key(key).requestHash("different").routeKey("merchant-42").build();
            assertThat(f.executor.execute(conflict, c -> { throw new AssertionError("conflicting callback"); }).getStatus()).isEqualTo(IdempotencyResultStatus.KEY_CONFLICT);
            var routeConflict = IdempotencyRequest.builder().key(key).requestHash("hash-" + key).routeKey("merchant-other").build();
            assertThat(f.executor.execute(routeConflict, c -> { throw new AssertionError("conflicting callback"); }).getStatus()).isEqualTo(IdempotencyResultStatus.KEY_CONFLICT);
            String failedKey = "capture-" + UUID.randomUUID();
            IdempotencyResultPolicy<String> badPolicy = IdempotencyResultPolicies.snapshot(new IdempotencyResultSerializer<>() {
                @Override public String serialize(String value) { throw new IllegalStateException("capture failure"); }
                @Override public String deserialize(String payload) { return payload; }
            });
            assertThat(f.executor.execute(request(failedKey), badPolicy, c -> f.insertOrder(failedKey)).getStatus()).isEqualTo(IdempotencyResultStatus.RESULT_POLICY_ERROR);
            f.assertOnlyTargetContains(failedKey, route, "FAILED", 0);
        }
        assertThat(f.routes.current()).isEmpty();
    }

    private static final class Fixture {
        final ThreadLocalStorageRouteContext routes = new ThreadLocalStorageRouteContext();
        final DirectStorageResourceRegistry resources = DirectStorageResourceRegistry.fromDataSources(SOURCES);
        final HashShardResolver hash = new HashShardResolver(10, 10);
        final AtomicInteger resolutions = new AtomicInteger();
        final List<Trace> trace = new ArrayList<>();
        final RouteMappingStrategy businessMapping;
        final RouteMappingStrategy idempotencyMapping;
        final DefaultStorageRouteToSqlRouteBridge bridge = new DefaultStorageRouteToSqlRouteBridge();
        final RoutedJdbcIdempotencyRepository repository;
        final RelationalTemplate relational;
        final IdempotencyExecutor executor;

        Fixture(TableIndexMode mode) {
            businessMapping = mapping(mode, "business_" + mode.name().toLowerCase(Locale.ROOT));
            idempotencyMapping = mapping(mode, "idempotency_" + mode.name().toLowerCase(Locale.ROOT));
            StorageRouteResolver delegate = new DefaultStorageRouteResolver(hash, businessMapping);
            StorageRouteResolver resolver = input -> { resolutions.incrementAndGet(); return delegate.resolve(input); };
            var jdbcRoutes = new StorageRoutingIdempotencyJdbcRouteResolver("iron_idempotency_record", "iron_idempotency_record", resolver, routes, idempotencyMapping, bridge);
            JdbcExecutionManagerResolver recording = new JdbcExecutionManagerResolver() {
                @Override public boolean supportsCurrentTransactionParticipation() { return true; }
                @Override public JdbcExecutionManager resolve(String key) {
                    return new RecordingJdbc(resources.jdbcExecutionManagerResolver().resolve(key), key, trace);
                }
            };
            repository = new RoutedJdbcIdempotencyRepository(jdbcRoutes, recording);
            var coordinator = new StorageRouteAwareIdempotencyTransactionCoordinator(routes, resources.transactionExecutorResolver());
            var policy = IdempotencyPolicy.builder().name("direct").mode(IdempotencyMode.DURABLE).recoveryPolicy(IdempotencyRecoveryPolicy.externalTask()).build();
            var core = new DefaultIdempotencyExecutor(new DefaultIdempotencyRepositoryRegistry(List.of(repository), "jdbc", "jdbc"),
                    new DefaultIdempotencyPolicyRegistry(List.of(policy), "direct"), (namespace, key) -> UUID.randomUUID().toString(),
                    (error, at) -> new IdempotencyFailureInfo("BUSINESS", error.getMessage(), true, at), null, coordinator, null, null, null, Clock.systemUTC());
            executor = new StorageRouteAwareIdempotencyExecutor(core, new DefaultIdempotencyRouteContextFactory("iron_idempotency_record"), resolver, routes);
            var connectionProvider = new SpringTransactionAwareConnectionProvider(context -> {
                var resource = resources.require(context.route().dataSourceKey());
                resource.assertCompatibleTransaction();
                return resource.dataSource();
            });
            relational = new DefaultRelationalTemplate(context -> {
                var handle = connectionProvider.acquire(context);
                trace.add(new Trace("BUSINESS", routes.requireCurrent().dataSourceKey(), handle.connection()));
                return handle;
            }, new StandardSqlExceptionTranslator());
        }

        StorageRoute businessRoute(int shard) {
            for (int order = 0; order < 100000; order++) {
                var key = CompositeShardKey.of(ShardKey.of("tenant", "t1"), ShardKey.of("order", order));
                var info = hash.resolve(key);
                if (info.shardId() == shard) return StorageRoute.builder().context(RouteContext.builder().logicalTable("business_order").shardKey(key).build())
                        .shardInfo(info).location(businessMapping.map(info)).build();
            }
            throw new AssertionError("cannot generate composite key for shard=" + shard);
        }

        String insertOrder(String key) {
            StorageRoute bound = routes.requireCurrent();
            // 默认 key 路由的 location 也不能直接当业务表；业务表族必须按同一 shardInfo 映射。
            StorageRoute business = StorageRoute.builder().context(bound.context()).shardInfo(bound.shardInfo()).location(businessMapping.map(bound.shardInfo())).build();
            relational.update(bridge.applyRoute(SqlStatement.of("order.insert", "INSERT INTO " + bridge.requireTableName(business) + " (order_id, amount) VALUES (?, ?)", key, 100), business));
            return key;
        }

        void assertSuccessfulTrace() {
            assertThat(trace).extracting(Trace::phase).containsExactly("NEW", "BUSINESS", "CURRENT");
            assertThat(trace.get(1).connection()).isSameAs(trace.get(2).connection()).isNotSameAs(trace.get(0).connection());
            assertThat(trace).extracting(Trace::dataSourceKey).containsOnly(trace.get(0).dataSourceKey());
        }

        void assertOnlyTargetContains(String key, StorageRoute target, String expectedState, int expectedBusinessRows) {
            int businessRows = 0;
            int idempotencyRows = 0;
            // 检查全部 100 个物理落点，防止“目标有数据，但别的库也误写了”漏检。
            for (int db = 0; db < 10; db++) {
                var source = new SingleConnectionDataSource();
                try {
                    source = new SingleConnectionDataSource(SOURCES.get(String.format(Locale.ROOT, "db_%02d", db)).getConnection(), true);
                    var jdbc = new JdbcTemplate(source);
                    for (int i = db * 10; i < db * 10 + 10; i++) {
                        var info = shard(i);
                        var business = businessMapping.map(info);
                        int count = jdbc.queryForObject("SELECT COUNT(*) FROM " + business.tableName() + " WHERE order_id=?", Integer.class, key);
                        businessRows += count;
                        var states = jdbc.queryForList("SELECT status FROM " + idempotencyMapping.map(info).tableName() + " WHERE idempotency_key=?", String.class, key);
                        idempotencyRows += states.size();
                        if (i == target.shardInfo().shardId()) {
                            assertThat(count).isEqualTo(expectedBusinessRows);
                            if (expectedState == null) assertThat(states).isEmpty(); else assertThat(states).containsExactly(expectedState);
                        } else {
                            assertThat(count).isZero();
                            assertThat(states).isEmpty();
                        }
                    }
                } catch (java.sql.SQLException error) {
                    throw new AssertionError("cannot inspect test shard", error);
                } finally {
                    source.destroy();
                }
            }
            assertThat(businessRows).isEqualTo(expectedBusinessRows);
            assertThat(idempotencyRows).isEqualTo(expectedState == null ? 0 : 1);
        }
    }

    private record Trace(String phase, String dataSourceKey, Connection connection) { }

    private record RecordingJdbc(JdbcExecutionManager delegate, String key, List<Trace> trace) implements JdbcExecutionManager {
        @Override public boolean supportsCurrentTransactionParticipation() { return true; }
        @Override public <T> T withConnection(JdbcWork<T> work) throws Exception { return delegate.withConnection(work); }
        @Override public <T> T inCurrentTransaction(JdbcWork<T> work) throws Exception {
            return delegate.inCurrentTransaction(c -> { trace.add(new Trace("CURRENT", key, c)); return work.execute(c); });
        }
        @Override public <T> T inNewTransaction(JdbcWork<T> work) throws Exception {
            return delegate.inNewTransaction(c -> { trace.add(new Trace("NEW", key, c)); return work.execute(c); });
        }
    }

    private static IdempotencyRequest request(String key) {
        return IdempotencyRequest.builder().key(key).requestHash("hash-" + key).routeKey("merchant-42").build();
    }

    private static ShardRouteInfo shard(int id) { return new ShardRouteInfo(id, id / 10, id % 10, 100); }

    private static RouteMappingStrategy mapping(TableIndexMode mode, String prefix) {
        return new RouteMappingStrategyFactory("db_", prefix, 2, 2).create(mode);
    }

    private static MysqlDataSource dataSource(String database) {
        var source = new MysqlDataSource();
        source.setURL("jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true");
        source.setUser("root");
        source.setPassword(MYSQL.getPassword());
        return source;
    }
}
