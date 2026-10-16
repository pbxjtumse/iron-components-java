package com.xjtu.iron.relational.mybatis.resource;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证 Direct 多数据源注册表不会混用 MyBatis 与事务资源。 */
class DirectMyBatisResourceRegistryTest {

    @Test
    void shouldExecuteMapperAgainstSelectedPhysicalDatabase() throws Exception {
        DataSource first = dataSource("mapper_first");
        DataSource second = dataSource("mapper_second");
        initialize(first, "first");
        initialize(second, "second");
        DirectMyBatisResourceRegistry registry = DirectMyBatisResourceRegistry.fromDataSources(
                Map.of("db-01", first, "db-02", second)
        );

        String firstMarker = registry.require("db-01").getMyBatisAccess().execute(
                "probe.first",
                DirectProbeMapper.class,
                DirectProbeMapper::selectMarker
        );
        String secondMarker = registry.require("db-02").getMyBatisAccess().execute(
                "probe.second",
                DirectProbeMapper.class,
                DirectProbeMapper::selectMarker
        );

        assertThat(firstMarker).isEqualTo("first");
        assertThat(secondMarker).isEqualTo("second");
    }

    @Test
    void shouldCommitMapperWorkThroughMatchingTransactionResource() throws Exception {
        DataSource first = dataSource("transaction_first");
        DataSource second = dataSource("transaction_second");
        initialize(first, "first");
        initialize(second, "second");
        DirectMyBatisResourceRegistry registry = DirectMyBatisResourceRegistry.fromDataSources(
                Map.of("db-01", first, "db-02", second)
        );

        registry.require("db-02").getMyBatisAccess().executeInNewTransaction(
                "probe.update",
                DirectProbeMapper.class,
                mapper -> mapper.updateMarker("updated-second")
        );

        assertThat(readMarker(registry, "db-01")).isEqualTo("first");
        assertThat(readMarker(registry, "db-02")).isEqualTo("updated-second");
    }

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

    private static void initialize(DataSource dataSource, String marker) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE direct_probe (id INTEGER PRIMARY KEY, marker VARCHAR(64) NOT NULL)");
            statement.execute("INSERT INTO direct_probe (id, marker) VALUES (1, '" + marker + "')");
        }
    }

    private static String readMarker(
            DirectMyBatisResourceRegistry registry,
            String dataSourceKey
    ) throws Exception {
        return registry.require(dataSourceKey).getMyBatisAccess().execute(
                "probe.read",
                DirectProbeMapper.class,
                DirectProbeMapper::selectMarker
        );
    }
}
