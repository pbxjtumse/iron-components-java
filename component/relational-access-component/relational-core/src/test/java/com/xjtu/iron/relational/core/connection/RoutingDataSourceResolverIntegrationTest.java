package com.xjtu.iron.relational.core.connection;

import com.xjtu.iron.relational.api.RelationalTemplate;
import com.xjtu.iron.relational.api.exception.RelationalAccessException;
import com.xjtu.iron.relational.api.exception.RelationalFailureType;
import com.xjtu.iron.relational.api.statement.SqlRoute;
import com.xjtu.iron.relational.api.statement.SqlStatement;
import com.xjtu.iron.relational.core.DefaultRelationalTemplate;
import com.xjtu.iron.relational.core.exception.StandardSqlExceptionTranslator;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingDataSourceResolverIntegrationTest {

    private JdbcDataSource defaultDataSource;
    private JdbcDataSource infraDataSource;
    private JdbcDataSource orderDataSource;
    private RelationalTemplate template;

    @BeforeEach
    void setUp() throws Exception {
        defaultDataSource = dataSource("default");
        infraDataSource = dataSource("infra");
        orderDataSource = dataSource("order");

        createTable(defaultDataSource);
        createTable(infraDataSource);
        createTable(orderDataSource);

        RoutingDataSourceResolver resolver = new RoutingDataSourceResolver(
                defaultDataSource,
                Map.of(
                        "infra-db", infraDataSource,
                        "order-db", orderDataSource
                )
        );
        template = new DefaultRelationalTemplate(
                new DefaultConnectionProvider(resolver),
                new StandardSqlExceptionTranslator()
        );
    }

    @Test
    void shouldResolveDefaultAndNamedDataSources() {
        insert("routing.default-insert", "default-row", SqlRoute.defaultRoute());
        insert("routing.infra-insert", "infra-row", SqlRoute.of("infra-db"));
        insert("routing.order-insert", "order-row", SqlRoute.of("order-db"));

        assertThat(count(SqlRoute.defaultRoute())).isEqualTo(1L);
        assertThat(count(SqlRoute.of("infra-db"))).isEqualTo(1L);
        assertThat(count(SqlRoute.of("order-db"))).isEqualTo(1L);

        assertThat(findName(SqlRoute.defaultRoute())).isEqualTo("default-row");
        assertThat(findName(SqlRoute.of("infra-db"))).isEqualTo("infra-row");
        assertThat(findName(SqlRoute.of("order-db"))).isEqualTo("order-row");
    }

    @Test
    void unknownNamedRouteShouldFailFast() {
        SqlStatement statement = SqlStatement.of(
                "routing.unknown",
                "SELECT COUNT(*) FROM routing_test"
        ).withRoute(SqlRoute.of("missing-db"));

        assertThatThrownBy(() -> template.queryScalar(statement, Long.class))
                .isInstanceOfSatisfying(RelationalAccessException.class, exception ->
                        assertThat(exception.failureType())
                                .isEqualTo(RelationalFailureType.DATA_SOURCE_ROUTING_ERROR));
    }

    @Test
    void constructorShouldRejectBlankRouteKey() {
        assertThatThrownBy(() -> new RoutingDataSourceResolver(
                defaultDataSource,
                Map.of(" ", infraDataSource)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dataSourceKey must not be blank");
    }

    private void insert(String operationName, String name, SqlRoute route) {
        template.update(SqlStatement.of(
                operationName,
                "INSERT INTO routing_test(name) VALUES (?)",
                name
        ).withRoute(route));
    }

    private long count(SqlRoute route) {
        return template.queryScalar(SqlStatement.of(
                "routing.count",
                "SELECT COUNT(*) FROM routing_test"
        ).withRoute(route), Long.class).orElseThrow();
    }

    private String findName(SqlRoute route) {
        return template.queryOne(SqlStatement.of(
                "routing.find-name",
                "SELECT name FROM routing_test"
        ).withRoute(route), resultSet -> resultSet.getString("name")).orElseThrow();
    }

    private static JdbcDataSource dataSource(String name) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:relational_route_" + name + "_"
                + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private static void createTable(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE routing_test (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        name VARCHAR(64) NOT NULL
                    )
                    """);
        }
    }
}
