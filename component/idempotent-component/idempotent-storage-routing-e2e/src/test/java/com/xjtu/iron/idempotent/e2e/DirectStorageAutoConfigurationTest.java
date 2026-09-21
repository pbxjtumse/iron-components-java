package com.xjtu.iron.idempotent.e2e;

import com.xjtu.iron.idempotent.api.execution.IdempotencyExecutor;
import com.xjtu.iron.idempotent.core.transaction.IdempotencyTransactionCoordinator;
import com.xjtu.iron.idempotent.integration.storage.routing.StorageRouteAwareIdempotencyTransactionCoordinator;
import com.xjtu.iron.idempotent.integration.transaction.DirectStorageResourceRegistry;
import com.xjtu.iron.idempotent.provider.jdbc.execution.JdbcExecutionManagerResolver;
import com.xjtu.iron.idempotent.starter.autoconfigure.*;
import com.xjtu.iron.storage.routing.starter.autoconfigure.StorageRoutingAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

class DirectStorageAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(StorageRoutingAutoConfiguration.class, IdempotencyStorageRoutingAutoConfiguration.class,
                    IdempotencyDirectStorageAutoConfiguration.class, IdempotencyAutoConfiguration.class, IdempotencyStorageRoutingExecutionAutoConfiguration.class))
            .withPropertyValues("xjtu.iron.idempotent.jdbc.direct.enabled=true", "xjtu.iron.idempotent.redis.enabled=false",
                    "xjtu.iron.storage-routing.resolver.enabled=true", "xjtu.iron.storage-routing.resolver.database-count=2",
                    "xjtu.iron.storage-routing.resolver.tables-per-database=10")
            .withBean("db_00", DriverManagerDataSource.class, DriverManagerDataSource::new);

    @Test
    void assemblesMultipleDataSourcesWithoutPrimaryOrSingleExecutor() {
        runner.withBean("db_01", DriverManagerDataSource.class, DriverManagerDataSource::new).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(DirectStorageResourceRegistry.class).hasSingleBean(JdbcExecutionManagerResolver.class);
            assertThat(context.getBean(IdempotencyTransactionCoordinator.class)).isInstanceOf(StorageRouteAwareIdempotencyTransactionCoordinator.class);
            assertThat(context.getBean(IdempotencyExecutor.class)).isNotNull();
            var registry = context.getBean(DirectStorageResourceRegistry.class);
            assertThat(registry.dataSources()).containsOnlyKeys("db_00", "db_01");
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
                .withPropertyValues("xjtu.iron.idempotent.jdbc.routing.enabled=false").run(context -> assertThat(context).hasFailed());
    }
}
