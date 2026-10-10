package com.xjtu.iron.idempotent.provider.mybatis.repository;

import com.xjtu.iron.idempotent.api.policy.IdempotencyMode;
import com.xjtu.iron.idempotent.api.policy.IdempotencyWindowPolicy;
import com.xjtu.iron.idempotent.api.recovery.IdempotencyRecoveryMode;
import com.xjtu.iron.idempotent.api.repository.acquire.IdempotencyAcquireRequest;
import com.xjtu.iron.idempotent.api.repository.acquire.IdempotencyAcquireResult;
import com.xjtu.iron.idempotent.api.repository.acquire.IdempotencyAcquireStatus;
import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryAcquireRequest;
import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryQuery;
import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryResult;
import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryStatus;
import com.xjtu.iron.idempotent.api.repository.write.IdempotencyFailureInfo;
import com.xjtu.iron.idempotent.api.repository.write.IdempotencyFailureRequest;
import com.xjtu.iron.idempotent.api.repository.write.IdempotencySuccessRequest;
import com.xjtu.iron.idempotent.api.repository.write.IdempotencyWriteStatus;
import com.xjtu.iron.idempotent.api.state.IdempotencyStatus;
import com.xjtu.iron.idempotent.api.storage.IdempotencyStorageContext;
import com.xjtu.iron.idempotent.provider.mybatis.mapper.IdempotencyMapper;
import com.xjtu.iron.relational.mybatis.MyBatisAccessListener;
import com.xjtu.iron.relational.mybatis.SpringMyBatisAccess;
import com.xjtu.iron.transaction.api.execution.TransactionExecutor;
import com.xjtu.iron.transaction.core.executor.DefaultTransactionExecutor;
import com.xjtu.iron.transaction.provider.spring.transaction.SpringTransactionProvider;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证 MyBatis Provider 的 Tx-A、Tx-B、Tx-C 边界和同库原子性。 */
class MyBatisIdempotencyTransactionTest {

    /** 测试使用的逻辑存储上下文。 */
    private static final IdempotencyStorageContext STORAGE =
            IdempotencyStorageContext.of("order", 7);

    /** 统一执行本地事务的组件入口。 */
    private TransactionExecutor transactions;

    /** 被测 MyBatis 幂等仓储。 */
    private MyBatisIdempotencyRepository repository;

    /** 与幂等表共享 DataSource 和 SqlSessionTemplate 的业务 Mapper。 */
    private BusinessOrderMapper businessOrders;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:idempotency_mybatis_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        createSchema(dataSource);

        Configuration configuration = new Configuration();
        configuration.addMapper(IdempotencyMapper.class);
        configuration.addMapper(BusinessOrderMapper.class);
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        SqlSessionFactory factory = factoryBean.getObject();
        SqlSessionTemplate template = new SqlSessionTemplate(factory);

        DataSourceTransactionManager transactionManager =
                new DataSourceTransactionManager(dataSource);
        transactions = new DefaultTransactionExecutor(
                new SpringTransactionProvider(transactionManager));
        repository = new MyBatisIdempotencyRepository(
                new SpringMyBatisAccess(
                        dataSource,
                        template,
                        transactions,
                        MyBatisAccessListener.noop()),
                "iron_idempotency_record");
        businessOrders = template.getMapper(BusinessOrderMapper.class);
    }

    @Test
    void txAShouldCommitAcquireEvenWhenOuterBusinessTransactionRollsBack() {
        assertThatThrownBy(() -> transactions.execute(context -> {
            assertThat(repository.tryAcquire(acquire("tx-a", "owner-a")).getStatus())
                    .isEqualTo(IdempotencyAcquireStatus.ACQUIRED);
            businessOrders.insert("order-a");
            throw new IllegalStateException("rollback business");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(businessOrders.count("order-a")).isZero();
        assertThat(repository.find(STORAGE, "order", "tx-a").orElseThrow().getStatus())
                .isEqualTo(IdempotencyStatus.PROCESSING);
    }

    @Test
    void txBShouldRollbackBusinessDataAndSuccessCasTogether() {
        IdempotencyAcquireResult acquired = repository.tryAcquire(acquire("tx-b", "owner-b"));

        assertThatThrownBy(() -> transactions.execute(context -> {
            businessOrders.insert("order-b");
            assertThat(repository.markSuccess(success("tx-b", acquired)).getStatus())
                    .isEqualTo(IdempotencyWriteStatus.UPDATED);
            throw new IllegalStateException("rollback both");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(businessOrders.count("order-b")).isZero();
        assertThat(repository.find(STORAGE, "order", "tx-b").orElseThrow().getStatus())
                .isEqualTo(IdempotencyStatus.PROCESSING);
    }

    @Test
    void txCShouldPersistFailureAfterBusinessTransactionHasRolledBack() {
        IdempotencyAcquireResult acquired = repository.tryAcquire(acquire("tx-c", "owner-c"));
        assertThatThrownBy(() -> transactions.execute(context -> {
            businessOrders.insert("order-c");
            throw new IllegalStateException("business failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(repository.markFailed(failure("tx-c", acquired)).getStatus())
                .isEqualTo(IdempotencyWriteStatus.UPDATED);
        assertThat(businessOrders.count("order-c")).isZero();
        assertThat(repository.find(STORAGE, "order", "tx-c").orElseThrow().getStatus())
                .isEqualTo(IdempotencyStatus.FAILED);
    }

    @Test
    void duplicateRequestShouldObserveActiveOwnerWithoutTakingOver() {
        IdempotencyAcquireResult first = repository.tryAcquire(acquire("duplicate", "owner-a"));
        IdempotencyAcquireResult duplicate = repository.tryAcquire(
                acquire("duplicate", "owner-b")
        );

        assertThat(first.getStatus()).isEqualTo(IdempotencyAcquireStatus.ACQUIRED);
        assertThat(duplicate.getStatus()).isEqualTo(IdempotencyAcquireStatus.PROCESSING_ACTIVE);
        assertThat(duplicate.getRecord().getOwnerToken()).isEqualTo("owner-a");
        assertThat(duplicate.getRecord().getVersion()).isEqualTo(1L);
    }

    @Test
    void ordinaryRequestShouldNotTakeOverExpiredProcessingButRecoveryCanCasIt() {
        Instant startedAt = Instant.parse("2026-10-10T00:00:00Z");
        IdempotencyAcquireResult first = repository.tryAcquire(acquire(
                "recovery",
                "owner-a",
                startedAt,
                Duration.ofMillis(10)
        ));
        IdempotencyAcquireResult ordinary = repository.tryAcquire(acquire(
                "recovery",
                "owner-b",
                startedAt.plusSeconds(1),
                Duration.ofSeconds(30)
        ));

        assertThat(ordinary.getStatus()).isEqualTo(IdempotencyAcquireStatus.PROCESSING_EXPIRED);
        IdempotencyRecoveryResult recovered = repository.tryRecover(recover(
                "recovery",
                "owner-b",
                first,
                startedAt.plusSeconds(1)
        ));
        assertThat(recovered.getStatus()).isEqualTo(IdempotencyRecoveryStatus.RECOVERY_ACQUIRED);
        assertThat(recovered.getRecord().getOwnerToken()).isEqualTo("owner-b");
        assertThat(recovered.getRecord().getVersion()).isEqualTo(2L);

        assertThat(repository.tryRecover(recover(
                "recovery",
                "owner-c",
                first,
                startedAt.plusSeconds(2)
        )).getStatus()).isEqualTo(IdempotencyRecoveryStatus.STALE_CANDIDATE);
    }

    @Test
    void recoveryScanShouldFilterByStoreNamespaceAndScanBucket() {
        Instant startedAt = Instant.parse("2026-10-10T00:00:00Z");
        repository.tryAcquire(acquire(
                "scan-candidate",
                "owner-a",
                startedAt,
                Duration.ofMillis(10)
        ));

        assertThat(repository.findRecoveryCandidates(new IdempotencyRecoveryQuery(
                STORAGE.getStoreName(),
                "order",
                STORAGE.getScanBucket(),
                startedAt.plusSeconds(1),
                10
        )))
                .singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.getKey()).isEqualTo("scan-candidate");
                    assertThat(candidate.getOwnerToken()).isEqualTo("owner-a");
                    assertThat(candidate.getVersion()).isEqualTo(1L);
                });
    }

    private static IdempotencyAcquireRequest acquire(String key, String owner) {
        return acquire(key, owner, Instant.now(), Duration.ofSeconds(30));
    }

    private static IdempotencyAcquireRequest acquire(
            String key,
            String owner,
            Instant now,
            Duration processingTimeout
    ) {
        return new IdempotencyAcquireRequest(
                STORAGE, "order", key, "hash", "merchant:1", owner,
                IdempotencyMode.DURABLE, processingTimeout, null,
                IdempotencyWindowPolicy.FIXED_FROM_FIRST_ACQUIRE,
                Duration.ZERO, IdempotencyRecoveryMode.EXTERNAL_TASK, now);
    }

    private static IdempotencyRecoveryAcquireRequest recover(
            String key,
            String newOwner,
            IdempotencyAcquireResult candidate,
            Instant now
    ) {
        return new IdempotencyRecoveryAcquireRequest(
                STORAGE,
                "order",
                key,
                "hash",
                "merchant:1",
                newOwner,
                candidate.getRecord().getOwnerToken(),
                candidate.getRecord().getVersion(),
                IdempotencyMode.DURABLE,
                Duration.ofSeconds(30),
                true,
                true,
                now
        );
    }

    private static IdempotencySuccessRequest success(
            String key,
            IdempotencyAcquireResult acquired
    ) {
        return new IdempotencySuccessRequest(
                STORAGE, "order", key, acquired.getRecord().getOwnerToken(),
                acquired.getRecord().getVersion(), "ok", IdempotencyMode.DURABLE,
                null, IdempotencyWindowPolicy.FIXED_FROM_FIRST_ACQUIRE,
                Duration.ZERO, Instant.now());
    }

    private static IdempotencyFailureRequest failure(
            String key,
            IdempotencyAcquireResult acquired
    ) {
        Instant now = Instant.now();
        return new IdempotencyFailureRequest(
                STORAGE, "order", key, acquired.getRecord().getOwnerToken(),
                acquired.getRecord().getVersion(),
                new IdempotencyFailureInfo("BUSINESS_FAILED", "failed", true, now),
                IdempotencyMode.DURABLE, null,
                IdempotencyWindowPolicy.FIXED_FROM_FIRST_ACQUIRE,
                Duration.ZERO, now);
    }

    private static void createSchema(JdbcDataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String schema = new String(
                    MyBatisIdempotencyTransactionTest.class.getResourceAsStream(
                            "/META-INF/iron-idempotency/mybatis/schema-h2.sql"
                    ).readAllBytes(),
                    StandardCharsets.UTF_8);
            for (String statement : schema.split(";")) {
                if (!statement.isBlank()) {
                    connection.createStatement().execute(statement);
                }
            }
            connection.createStatement().execute(
                    "CREATE TABLE business_order (order_id VARCHAR(64) PRIMARY KEY)");
        }
    }

    /** 测试业务表的 MyBatis Mapper。 */
    interface BusinessOrderMapper {

        @Insert("INSERT INTO business_order(order_id) VALUES (#{orderId})")
        int insert(@Param("orderId") String orderId);

        @Select("SELECT COUNT(*) FROM business_order WHERE order_id = #{orderId}")
        long count(@Param("orderId") String orderId);
    }
}
