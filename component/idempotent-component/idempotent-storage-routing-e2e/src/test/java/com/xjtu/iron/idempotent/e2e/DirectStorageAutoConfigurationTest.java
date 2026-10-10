package com.xjtu.iron.idempotent.e2e;

import com.xjtu.iron.idempotent.api.execution.IdempotencyExecutor;
import com.xjtu.iron.idempotent.core.transaction.IdempotencyTransactionCoordinator;
import com.xjtu.iron.idempotent.integration.storage.routing.StorageRouteAwareIdempotencyTransactionCoordinator;
import com.xjtu.iron.relational.mybatis.DirectMyBatisResourceRegistry;
import com.xjtu.iron.relational.mybatis.MyBatisAccessResolver;
import com.xjtu.iron.idempotent.starter.autoconfigure.*;
import com.xjtu.iron.relational.spring.boot.autoconfigure.RelationalAccessAutoConfiguration;
import com.xjtu.iron.storage.routing.starter.autoconfigure.StorageRoutingAutoConfiguration;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

class DirectStorageAutoConfigurationTest {
    /** 多物理数据源 Direct 模式的基础上下文。 */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(StorageRoutingAutoConfiguration.class, IdempotencyStorageRoutingAutoConfiguration.class,
                    IdempotencyDirectStorageAutoConfiguration.class, IdempotencyAutoConfiguration.class, IdempotencyStorageRoutingExecutionAutoConfiguration.class))
            .withPropertyValues("xjtu.iron.idempotent.mybatis.direct.enabled=true", "xjtu.iron.idempotent.redis.enabled=false",
                    "xjtu.iron.storage-routing.resolver.enabled=true", "xjtu.iron.storage-routing.resolver.type=HASH",
                    "xjtu.iron.storage-routing.resolver.hash.database-count=2",
                    "xjtu.iron.storage-routing.resolver.hash.tables-per-database=10")
            .withBean("db_00", DriverManagerDataSource.class, DriverManagerDataSource::new);

    @Test
    void assemblesMultipleDataSourcesWithoutPrimaryOrSingleExecutor() {
        runner.withBean("db_01", DriverManagerDataSource.class, DriverManagerDataSource::new).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(DirectMyBatisResourceRegistry.class).hasSingleBean(MyBatisAccessResolver.class);
            assertThat(context.getBean(IdempotencyTransactionCoordinator.class)).isInstanceOf(StorageRouteAwareIdempotencyTransactionCoordinator.class);
            assertThat(context.getBean(IdempotencyExecutor.class)).isNotNull();
            assertThat(context).hasSingleBean(com.xjtu.iron.idempotent.api.result.IdempotencySnapshotPolicyFactory.class);
            assertThat(context).hasSingleBean(com.xjtu.iron.idempotent.api.spi.IdempotencyRequestHasher.class);
            var registry = context.getBean(DirectMyBatisResourceRegistry.class);
            assertThat(registry.getDataSources()).containsOnlyKeys("db_00", "db_01");
        });
    }

    @Test
    void optionalJacksonAndMicrometerAreNotRequiredToAssembleDirectStorage() {
        runner.withBean("db_01", DriverManagerDataSource.class, DriverManagerDataSource::new)
                .withClassLoader(new FilteredClassLoader("com.fasterxml.jackson", "io.micrometer"))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(DirectMyBatisResourceRegistry.class);
                    assertThat(context.getBean(IdempotencyExecutor.class)).isNotNull();
                    assertThat(context).doesNotHaveBean(com.xjtu.iron.idempotent.api.result.IdempotencySnapshotPolicyFactory.class);
                    assertThat(context).doesNotHaveBean(com.xjtu.iron.idempotent.api.spi.IdempotencyRequestHasher.class);
                });
    }

    @Test
    void rejectsMissingShardAtStartup() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsDisabledTransactionAndDisabledRouting() {
        runner.withBean("db_01", DriverManagerDataSource.class, DriverManagerDataSource::new)
                .withPropertyValues("xjtu.iron.idempotent.transaction.enabled=false").run(context -> assertThat(context).hasFailed());
        runner.withBean("db_01", DriverManagerDataSource.class, DriverManagerDataSource::new)
                .withPropertyValues("xjtu.iron.idempotent.mybatis.routing.enabled=false").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void reportsMissingStorageRouteResolverClearly() {
        runner.withBean("db_01", DriverManagerDataSource.class, DriverManagerDataSource::new)
                .withPropertyValues("xjtu.iron.storage-routing.resolver.enabled=false")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining(
                            "enable xjtu.iron.storage-routing.resolver or define one custom StorageRouteResolver bean");
                });
    }

    @Test
    void directResolverShouldBePrimaryWhenSingleDatasourceAccessAlsoExists() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        MybatisAutoConfiguration.class,
                        RelationalAccessAutoConfiguration.class,
                        StorageRoutingAutoConfiguration.class,
                        IdempotencyStorageRoutingAutoConfiguration.class,
                        IdempotencyDirectStorageAutoConfiguration.class,
                        IdempotencyAutoConfiguration.class,
                        IdempotencyStorageRoutingExecutionAutoConfiguration.class
                ))
                .withPropertyValues(
                        "xjtu.iron.idempotent.mybatis.direct.enabled=true",
                        "xjtu.iron.idempotent.redis.enabled=false",
                        "xjtu.iron.storage-routing.resolver.enabled=true",
                        "xjtu.iron.storage-routing.resolver.type=HASH",
                        "xjtu.iron.storage-routing.resolver.hash.database-count=1",
                        "xjtu.iron.storage-routing.resolver.hash.tables-per-database=1"
                )
                .withBean("db_00", DriverManagerDataSource.class, DriverManagerDataSource::new)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(MyBatisAccessResolver.class)).hasSize(2);
                    assertThat(context.getBean(MyBatisAccessResolver.class)).isSameAs(
                            context.getBean(DirectMyBatisResourceRegistry.class)
                                    .getMyBatisAccessResolver()
                    );
                });
    }
}
