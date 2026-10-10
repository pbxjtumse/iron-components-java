package com.xjtu.iron.relational.mybatis;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证 Direct 多数据源注册表不会混用 MyBatis 与事务资源。 */
class DirectMyBatisResourceRegistryTest {

    @Test
    void shouldResolveAccessAndTransactionExecutorFromSamePhysicalResource() {
        DataSource first = dataSource("first");
        DataSource second = dataSource("second");
        DirectMyBatisResourceRegistry registry = DirectMyBatisResourceRegistry.fromDataSources(
                Map.of("db-01", first, "db-02", second)
        );

        assertThat(registry.require("db-01").getDataSource()).isSameAs(first);
        assertThat(registry.require("db-02").getDataSource()).isSameAs(second);
        assertThat(registry.getMyBatisAccessResolver().resolve("db-01"))
                .isSameAs(registry.require("db-01").getMyBatisAccess());
        assertThat(registry.getTransactionExecutorResolver().resolve("db-02"))
                .isSameAs(registry.require("db-02").getTransactionExecutor());
    }

    @Test
    void shouldRejectOneDataSourceRegisteredUnderMultipleKeys() {
        DataSource shared = dataSource("shared");

        assertThatThrownBy(() -> new DirectMyBatisResourceRegistry(List.of(
                new DirectMyBatisResource("db-01", shared),
                new DirectMyBatisResource("db-02", shared)
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("multiple keys");
    }

    @Test
    void shouldRejectUnknownPhysicalDataSourceKey() {
        DirectMyBatisResourceRegistry registry = DirectMyBatisResourceRegistry.fromDataSources(
                Map.of("db-01", dataSource("known"))
        );

        assertThatThrownBy(() -> registry.getMyBatisAccessResolver().resolve("db-missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("db-missing");
        assertThatThrownBy(() -> registry.getTransactionExecutorResolver().resolve("db-missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("db-missing");
    }

    private static DataSource dataSource(String name) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:direct_registry_" + name + ";DB_CLOSE_DELAY=-1");
        return dataSource;
    }
}
