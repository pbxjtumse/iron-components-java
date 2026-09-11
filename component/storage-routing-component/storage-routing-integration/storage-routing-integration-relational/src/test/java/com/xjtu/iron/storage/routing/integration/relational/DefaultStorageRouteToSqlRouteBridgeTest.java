package com.xjtu.iron.storage.routing.integration.relational;

import com.xjtu.iron.relational.api.RelationalTemplate;
import com.xjtu.iron.relational.api.statement.SqlRoute;
import com.xjtu.iron.relational.api.statement.SqlStatement;
import com.xjtu.iron.relational.core.DefaultRelationalTemplate;
import com.xjtu.iron.relational.core.connection.DefaultConnectionProvider;
import com.xjtu.iron.relational.core.connection.RoutingDataSourceResolver;
import com.xjtu.iron.relational.core.exception.StandardSqlExceptionTranslator;
import com.xjtu.iron.storage.routing.api.StorageRoute;
import com.xjtu.iron.storage.routing.api.StorageRouteMode;
import com.xjtu.iron.storage.routing.api.StorageRoutingException;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultStorageRouteToSqlRouteBridgeTest {

    private final StorageRouteToSqlRouteBridge bridge = new DefaultStorageRouteToSqlRouteBridge();

    @Test
    void directRouteShouldExposeSqlRouteAndPhysicalTableNameSeparately() {
        StorageRoute route = StorageRoute.direct("iron_idempotency_record", "order-db-05", "iron_idempotency_record_56");
        SqlStatement statement = SqlStatement.of("idempotency.insert",
                "INSERT INTO " + bridge.requireTableName(route) + "(id, name) VALUES (?, ?)", 1L, "ok");

        assertThat(bridge.toSqlRoute(route)).isEqualTo(SqlRoute.of("order-db-05"));
        assertThat(bridge.requireTableName(route)).isEqualTo("iron_idempotency_record_56");
        assertThat(bridge.applyRoute(statement, route).route()).isEqualTo(SqlRoute.of("order-db-05"));
        assertThat(bridge.applyRoute(statement, route).sql()).contains("iron_idempotency_record_56");
    }

    @ParameterizedTest
    @EnumSource(value = StorageRouteMode.class, names = {"SHARDINGSPHERE_JDBC", "PROXY"})
    void defaultBridgeShouldRejectModesThatNeedDedicatedAdapters(StorageRouteMode mode) {
        StorageRoute route = StorageRoute.builder().mode(mode).logicalTable("iron_idempotency_record").build();

        assertThatThrownBy(() -> bridge.toSqlRoute(route))
                .isInstanceOf(StorageRoutingException.class)
                .hasMessageContaining("DIRECT_DATASOURCE");
    }

    @Test
    void bridgedRouteShouldDriveRelationalTemplateToTheTargetDataSource() throws Exception {
        JdbcDataSource defaultDataSource = dataSource("default");
        JdbcDataSource orderDataSource = dataSource("order");
        createTable(defaultDataSource, "iron_idempotency_record_56");
        createTable(orderDataSource, "iron_idempotency_record_56");
        RelationalTemplate template = new DefaultRelationalTemplate(
                new DefaultConnectionProvider(new RoutingDataSourceResolver(
                        defaultDataSource, Map.of("order-db-05", orderDataSource))),
                new StandardSqlExceptionTranslator());
        StorageRoute route = StorageRoute.direct("iron_idempotency_record", "order-db-05", "iron_idempotency_record_56");

        template.update(bridge.applyRoute(SqlStatement.of("idempotency.insert",
                "INSERT INTO " + bridge.requireTableName(route) + "(id, name) VALUES (?, ?)", 1L, "stored"), route));

        assertThat(countRouted(template, route)).isEqualTo(1L);
        assertThat(countDefault(template, bridge.requireTableName(route))).isEqualTo(0L);
    }

    private long countRouted(RelationalTemplate template, StorageRoute route) {
        return template.queryScalar(SqlStatement.of("idempotency.count",
                "SELECT COUNT(*) FROM " + bridge.requireTableName(route)).withRoute(bridge.toSqlRoute(route)), Long.class)
                .orElseThrow();
    }

    private static long countDefault(RelationalTemplate template, String tableName) {
        return template.queryScalar(SqlStatement.of("idempotency.count-default",
                "SELECT COUNT(*) FROM " + tableName).withRoute(SqlRoute.defaultRoute()), Long.class).orElseThrow();
    }

    private static JdbcDataSource dataSource(String name) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:storage_route_bridge_" + name + "_"
                + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private static void createTable(DataSource dataSource, String tableName) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE " + tableName + " (id BIGINT PRIMARY KEY, name VARCHAR(64) NOT NULL)");
        }
    }
}
