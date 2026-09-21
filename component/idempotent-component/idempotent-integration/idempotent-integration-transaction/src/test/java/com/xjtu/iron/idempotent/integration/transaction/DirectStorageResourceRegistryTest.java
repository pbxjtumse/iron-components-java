package com.xjtu.iron.idempotent.integration.transaction;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class DirectStorageResourceRegistryTest {
    @Test
    void buildsJdbcAndTransactionResolversFromIdenticalResources() {
        var source = new DriverManagerDataSource();
        var registry = DirectStorageResourceRegistry.fromDataSources(Map.of("db_03", source));
        var resource = registry.require("db_03");
        assertThat(registry.dataSources().get("db_03")).isSameAs(source);
        assertThat(registry.jdbcExecutionManagerResolver().resolve("db_03")).isSameAs(resource.jdbcExecutionManager());
        assertThat(registry.transactionExecutorResolver().resolve("db_03")).isSameAs(resource.transactionExecutor());
        assertThat(registry.jdbcExecutionManagerResolver().supportsCurrentTransactionParticipation()).isTrue();
        assertThatThrownBy(() -> registry.require("db_04")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.jdbcExecutionManagerResolver().resolve(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEmptyDuplicateAndAliasedResources() {
        var source = new DriverManagerDataSource();
        assertThatThrownBy(() -> new DirectStorageResourceRegistry(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DirectStorageResourceRegistry.fromDataSources(Map.of("db_00", source, "db_01", source)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("multiple keys");
        assertThatThrownBy(() -> new DirectStorageResourceRegistry(List.of(new DirectStorageResource("db_00", source), new DirectStorageResource(" db_00 ", new DriverManagerDataSource()))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate");
    }

    @Test
    void synchronizationOnlyConnectionDoesNotCountAsAnActualLocalTransaction() {
        var resource = new DirectStorageResource("db_03", new DriverManagerDataSource());
        var connection = (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {java.sql.Connection.class},
                (proxy, method, args) -> { throw new AssertionError("binding inspection must not access JDBC: " + method.getName()); });
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.bindResource(resource.dataSource(), new ConnectionHolder(connection));
        try {
            assertThatThrownBy(resource::assertCompatibleTransaction).isInstanceOf(IllegalStateException.class).hasMessageContaining("another resource");
        } finally {
            TransactionSynchronizationManager.unbindResource(resource.dataSource());
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void rejectsForeignTransactionBeforeOpeningAnyConnection() {
        var resource = new DirectStorageResource("db_03", new DriverManagerDataSource());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThatThrownBy(() -> resource.transactionExecutor().execute(context -> "must not run"))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("another resource");
            assertThatThrownBy(() -> resource.jdbcExecutionManager().inCurrentTransaction(connection -> "must not run"))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("does not bind");
            assertThat(TransactionSynchronizationManager.hasResource(resource.dataSource())).isFalse();
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }
}
