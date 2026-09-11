package com.xjtu.iron.idempotent.starter.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjtu.iron.distributed.lock.api.client.DistributedLockClient;
import com.xjtu.iron.idempotent.api.execution.*;
import com.xjtu.iron.idempotent.api.policy.*;
import com.xjtu.iron.idempotent.api.recovery.*;
import com.xjtu.iron.idempotent.api.repository.IdempotencyRepository;
import com.xjtu.iron.idempotent.api.result.IdempotencySnapshotPolicyFactory;
import com.xjtu.iron.idempotent.api.spi.IdempotencyFailureClassifier;
import com.xjtu.iron.idempotent.api.spi.IdempotencyRequestHasher;
import com.xjtu.iron.idempotent.core.execution.*;
import com.xjtu.iron.idempotent.core.failure.*;
import com.xjtu.iron.idempotent.core.owner.*;
import com.xjtu.iron.idempotent.core.policy.*;
import com.xjtu.iron.idempotent.core.recovery.*;
import com.xjtu.iron.idempotent.core.repository.*;
import com.xjtu.iron.idempotent.core.observation.IdempotencyEventPublisher;
import com.xjtu.iron.idempotent.core.observation.IdempotencyMetrics;
import com.xjtu.iron.idempotent.core.state.DefaultIdempotencyStateMachine;
import com.xjtu.iron.idempotent.core.state.IdempotencyStateMachine;
import com.xjtu.iron.idempotent.core.transaction.IdempotencyTransactionCoordinator;
import com.xjtu.iron.idempotent.integration.transaction.SpringTransactionJdbcExecutionManager;
import com.xjtu.iron.idempotent.integration.transaction.TransactionTemplateIdempotencyTransactionCoordinator;
import com.xjtu.iron.idempotent.provider.jdbc.execution.DataSourceJdbcExecutionManager;
import com.xjtu.iron.idempotent.provider.jdbc.execution.JdbcExecutionManager;
import com.xjtu.iron.idempotent.provider.jdbc.repository.JdbcIdempotencyRepository;
import com.xjtu.iron.idempotent.provider.redis.repository.RedisIdempotencyRepository;
import com.xjtu.iron.idempotent.starter.hash.JacksonSha256IdempotencyRequestHasher;
import com.xjtu.iron.idempotent.starter.observation.JacksonIdempotencySnapshotPolicyFactory;
import com.xjtu.iron.idempotent.starter.observation.MicrometerIdempotencyMetrics;
import com.xjtu.iron.idempotent.starter.properties.IdempotencyProperties;
import com.xjtu.iron.idempotent.starter.result.SpringIdempotencyEventPublisher;
import com.xjtu.iron.transaction.api.execution.TransactionExecutor;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 幂等组件自动装配入口。
 *
 * <p>Starter 只负责“把现有能力拼起来”，不承载幂等状态机。装配顺序可以理解为：</p>
 * <pre>
 * 基础 SPI（owner/failure/hash/result factory）
 *   -> Repository（Redis/JDBC）
 *   -> transaction-aware JdbcExecutionManager / TransactionCoordinator
 *   -> RepositoryRegistry
 *   -> PolicyRegistry
 *   -> DefaultIdempotencyExecutor
 *   -> RecoveryQueryService
 * </pre>
 *
 * <p>尤其注意：是否启用 Tx-B 不是单靠配置 boolean 决定，而是 TransactionExecutor 存在 + JDBC execution manager
 * 真正支持 current transaction participation 两个条件共同决定。</p>
 */
@AutoConfiguration(afterName = "com.xjtu.iron.transaction.starter.autoconfigure.TransactionAutoConfiguration")
@EnableConfigurationProperties(IdempotencyProperties.class)
@ConditionalOnProperty(prefix = "xjtu.iron.idempotent", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IdempotencyAutoConfiguration {

    // -------------------- 基础扩展点 --------------------

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyOwnerTokenGenerator idempotencyOwnerTokenGenerator() {
        return new UuidIdempotencyOwnerTokenGenerator();
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyFailureClassifier idempotencyFailureClassifier() {
        return new DefaultIdempotencyFailureClassifier();
    }

    /**
     * 结果快照不再通过 Executor 的 Class<T> 处理。
     * Jackson 只作为 ResultPolicy 的可选类型安全工厂存在。
     */
    @Bean
    @ConditionalOnClass(ObjectMapper.class)
    @ConditionalOnMissingBean(IdempotencySnapshotPolicyFactory.class)
    public IdempotencySnapshotPolicyFactory idempotencySnapshotPolicyFactory(ObjectProvider<ObjectMapper> provider) {
        return new JacksonIdempotencySnapshotPolicyFactory(provider.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    @ConditionalOnClass(ObjectMapper.class)
    @ConditionalOnMissingBean(IdempotencyRequestHasher.class)
    public IdempotencyRequestHasher idempotencyRequestHasher(ObjectProvider<ObjectMapper> provider) {
        return new JacksonSha256IdempotencyRequestHasher(provider.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    @ConditionalOnMissingBean(IdempotencyEventPublisher.class)
    public IdempotencyEventPublisher idempotencyEventPublisher(ApplicationEventPublisher publisher) {
        return new SpringIdempotencyEventPublisher(publisher);
    }

    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean(IdempotencyMetrics.class)
    public IdempotencyMetrics idempotencyMetrics(MeterRegistry registry) {
        return new MicrometerIdempotencyMetrics(registry);
    }

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock idempotencyClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(IdempotencyStateMachine.class)
    public IdempotencyStateMachine idempotencyStateMachine() {
        return new DefaultIdempotencyStateMachine();
    }

    // -------------------- Repository Provider --------------------

    @Bean(name = "redisIdempotencyRepository")
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnProperty(prefix = "xjtu.iron.idempotent.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
    public IdempotencyRepository redisIdempotencyRepository(StringRedisTemplate redis, IdempotencyProperties properties) {
        return new RedisIdempotencyRepository(redis, properties.getRedis().getKeyPrefix());
    }

    /**
     * 为 JDBC Repository 选择 Connection/事务执行方式。
     *
     * <p>有 transaction-component 时使用 SpringTransactionJdbcExecutionManager，使 Tx-A/Tx-C 走 REQUIRES_NEW，
     * markSuccess 能复用 Tx-B 当前 Connection；没有事务模板时退化为普通 DataSource 模式，但不会宣称业务与 SUCCESS 原子。</p>
     */
    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(JdbcExecutionManager.class)
    public JdbcExecutionManager idempotencyJdbcExecutionManager(
            DataSource dataSource,
            ObjectProvider<TransactionExecutor> transactionExecutor,
            IdempotencyProperties properties) {

        TransactionExecutor executor = transactionExecutor.getIfAvailable();
        if (properties.getTransaction().isEnabled() && executor != null) {
            return new SpringTransactionJdbcExecutionManager(dataSource, executor);
        }

        if (properties.getTransaction().isEnabled() && properties.getTransaction().isRequireTemplate() && executor == null) {
            throw new IllegalStateException(
                    "xjtu.iron.idempotent.transaction.require-template=true, "
                            + "but no transaction-component TransactionExecutor bean is available");
        }

        return new DataSourceJdbcExecutionManager(dataSource);
    }

    /**
     * Tx-B Coordinator：只负责 REQUIRED 业务事务边界，不负责 Tx-A/Tx-C 的 Connection 获取。
     */
    @Bean
    @ConditionalOnBean(TransactionExecutor.class)
    @ConditionalOnProperty(prefix = "xjtu.iron.idempotent.transaction", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(IdempotencyTransactionCoordinator.class)
    public IdempotencyTransactionCoordinator idempotencyTransactionCoordinator(TransactionExecutor transactionExecutor) {
        return new TransactionTemplateIdempotencyTransactionCoordinator(transactionExecutor);
    }

    @Bean(name = "jdbcIdempotencyRepository")
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "xjtu.iron.idempotent.jdbc", name = "enabled", havingValue = "true", matchIfMissing = true)
    public IdempotencyRepository jdbcIdempotencyRepository(JdbcExecutionManager jdbc, IdempotencyProperties properties) {
        return new JdbcIdempotencyRepository(jdbc, properties.getJdbc().getTableName());
    }

    // -------------------- Registry / Policy --------------------

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyRepositoryRegistry idempotencyRepositoryRegistry(
            List<IdempotencyRepository> repositories,
            IdempotencyProperties properties) {
        return new DefaultIdempotencyRepositoryRegistry(
                repositories,
                properties.getDefaultWindowedRepository(),
                properties.getDefaultDurableRepository());
    }

    /**
     * 组装两个内建默认 Policy + application.yml 中的自定义命名 Policy。
     */
    @Bean
    @ConditionalOnMissingBean
    public IdempotencyPolicyRegistry idempotencyPolicyRegistry(IdempotencyProperties properties) {

        List<IdempotencyPolicy> policies = new ArrayList<>();
        IdempotencyLockOptions globalLock = lockOptions(properties.getLock());

        IdempotencyProperties.Windowed windowed = properties.getWindowed();
        // 内建默认策略可以被同名 application.yml 策略显式覆盖，
        // 避免“用户想调整默认策略却因为重复 name 启动失败”。
        if (!properties.getPolicies().containsKey("windowed-default")) {
            policies.add(IdempotencyPolicy.builder()
                    .name("windowed-default")
                    .mode(IdempotencyMode.WINDOWED)
                    .repositoryName(properties.getDefaultWindowedRepository())
                    .processingTimeout(properties.getProcessingTimeout())
                    .idempotencyWindow(windowed.getIdempotencyWindow())
                    .windowPolicy(windowed.getWindowPolicy())
                    .recordRetentionTtl(windowed.getRecordRetentionTtl())
                    .recoveryPolicy(IdempotencyRecoveryPolicy.builder()
                            .mode(windowed.getRecoveryMode())
                            .recoverProcessingTimeout(windowed.isRecoverProcessingTimeout())
                            .recoverRetryableFailure(windowed.isRecoverFailed())
                            .build())
                    .lockOptions(globalLock)
                    .build());
        }

        IdempotencyProperties.Durable durable = properties.getDurable();
        if (!properties.getPolicies().containsKey("durable-default")) {
            policies.add(IdempotencyPolicy.builder()
                    .name("durable-default")
                    .mode(IdempotencyMode.DURABLE)
                    .repositoryName(properties.getDefaultDurableRepository())
                    .processingTimeout(properties.getProcessingTimeout())
                    .recoveryPolicy(IdempotencyRecoveryPolicy.builder()
                            .mode(durable.getRecoveryMode())
                            .recoverProcessingTimeout(durable.isRecoverProcessingTimeout())
                            .recoverRetryableFailure(durable.isRecoverFailed())
                            .build())
                    .lockOptions(globalLock)
                    .build());
        }

        for (Map.Entry<String, IdempotencyProperties.Policy> entry : properties.getPolicies().entrySet()) {
            policies.add(buildNamedPolicy(entry.getKey(), entry.getValue(), properties, globalLock));
        }

        return new DefaultIdempotencyPolicyRegistry(policies, properties.getDefaultPolicy());
    }

    // -------------------- Core runtime --------------------

    /**
     * 组装唯一的主执行器。业务代码最终只需要依赖 IdempotencyExecutor。
     */
    @Bean
    @ConditionalOnMissingBean(IdempotencyExecutor.class)
    public IdempotencyExecutor idempotencyExecutor(
            IdempotencyRepositoryRegistry repositoryRegistry,
            IdempotencyPolicyRegistry policyRegistry,
            IdempotencyOwnerTokenGenerator ownerGenerator,
            IdempotencyFailureClassifier failureClassifier,
            ObjectProvider<DistributedLockClient> lockClient,
            ObjectProvider<IdempotencyTransactionCoordinator> transactionCoordinator,
            IdempotencyStateMachine stateMachine,
            ObjectProvider<IdempotencyEventPublisher> eventPublisher,
            ObjectProvider<IdempotencyMetrics> metrics,
            Clock clock) {

        return new DefaultIdempotencyExecutor(
                repositoryRegistry,
                policyRegistry,
                ownerGenerator,
                failureClassifier,
                lockClient.getIfAvailable(),
                transactionCoordinator.getIfAvailable(),
                stateMachine,
                eventPublisher.getIfAvailable(IdempotencyEventPublisher::noop),
                metrics.getIfAvailable(IdempotencyMetrics::noop),
                clock);
    }

    @Bean
    @ConditionalOnMissingBean(IdempotencyRecoveryQueryService.class)
    public IdempotencyRecoveryQueryService idempotencyRecoveryQueryService(
            IdempotencyRepositoryRegistry registry,
            IdempotencyPolicyRegistry policyRegistry) {
        return new DefaultIdempotencyRecoveryQueryService(registry, policyRegistry);
    }

    private IdempotencyPolicy buildNamedPolicy(
            String name,
            IdempotencyProperties.Policy source,
            IdempotencyProperties root,
            IdempotencyLockOptions globalLock) {

        IdempotencyMode mode;
        if (source.getMode() != null) {
            mode = source.getMode();
        } else if ("windowed-default".equals(name)) {
            mode = IdempotencyMode.WINDOWED;
        } else {
            // durable-default 以及普通自定义 Policy 在未声明 mode 时都以 DURABLE 为安全默认。
            mode = IdempotencyMode.DURABLE;
        }
        boolean windowed = mode.isWindowed();

        Duration processingTimeout = source.getProcessingTimeout() == null
                ? root.getProcessingTimeout() : source.getProcessingTimeout();

        IdempotencyRecoveryMode recoveryMode = source.getRecoveryMode();
        if (recoveryMode == null) {
            recoveryMode = windowed
                    ? root.getWindowed().getRecoveryMode()
                    : root.getDurable().getRecoveryMode();
        }

        boolean recoverProcessing = source.getRecoverProcessingTimeout() != null
                ? source.getRecoverProcessingTimeout()
                : (windowed ? root.getWindowed().isRecoverProcessingTimeout() : root.getDurable().isRecoverProcessingTimeout());

        boolean recoverFailed = source.getRecoverFailed() != null
                ? source.getRecoverFailed()
                : (windowed ? root.getWindowed().isRecoverFailed() : root.getDurable().isRecoverFailed());

        // 命名 Policy 的 null 字段统一在这里继承全局默认值，Core 层拿到的是已闭合的稳定策略。
        IdempotencyPolicy.Builder builder = IdempotencyPolicy.builder()
                .name(name)
                .mode(mode)
                .namespace(source.getNamespace())
                .repositoryName(source.getRepositoryName() == null
                        ? (windowed ? root.getDefaultWindowedRepository() : root.getDefaultDurableRepository())
                        : source.getRepositoryName())
                .processingTimeout(processingTimeout)
                .recoveryPolicy(IdempotencyRecoveryPolicy.builder()
                        .mode(recoveryMode)
                        .recoverProcessingTimeout(recoverProcessing)
                        .recoverRetryableFailure(recoverFailed)
                        .build())
                .lockOptions(mergeLock(source, globalLock));

        if (windowed) {
            // DURABLE 不设置 idempotencyWindow；WINDOWED 才需要窗口与保留时间。
            builder.idempotencyWindow(
                            source.getIdempotencyWindow() == null
                                    ? root.getWindowed().getIdempotencyWindow()
                                    : source.getIdempotencyWindow())
                    .windowPolicy(source.getWindowPolicy() == null ? root.getWindowed().getWindowPolicy() : source.getWindowPolicy())
                    .recordRetentionTtl(
                            source.getRecordRetentionTtl() == null
                                    ? root.getWindowed().getRecordRetentionTtl()
                                    : source.getRecordRetentionTtl());
        }

        return builder.build();
    }

    private IdempotencyLockOptions mergeLock(IdempotencyProperties.Policy policy, IdempotencyLockOptions global) {

        // lockEnabled=null 表示继承全局短锁开关；false 是显式关闭当前 Policy 短锁。
        boolean enabled = policy.getLockEnabled() == null
                ? global.isEnabled() : policy.getLockEnabled();

        return IdempotencyLockOptions.builder()
                .enabled(enabled)
                .providerName(policy.getLockProviderName() == null ? global.getProviderName() : policy.getLockProviderName())
                .waitTime(policy.getLockWaitTime() == null ? global.getWaitTime() : policy.getLockWaitTime())
                .leaseTime(policy.getLockLeaseTime() == null ? global.getLeaseTime() : policy.getLockLeaseTime())
                .fallbackToStateOnFailure(
                        policy.getLockFallbackToStateOnFailure() == null
                                ? global.isFallbackToStateOnFailure()
                                : policy.getLockFallbackToStateOnFailure())
                .build();
    }

    private IdempotencyLockOptions lockOptions(IdempotencyProperties.Lock lock) {
        return IdempotencyLockOptions.builder()
                .enabled(lock.isEnabled())
                .providerName(lock.getProviderName())
                .waitTime(lock.getWaitTime())
                .leaseTime(lock.getLeaseTime())
                .fallbackToStateOnFailure(lock.isFallbackToStateOnFailure())
                .build();
    }
}
