package com.xjtu.iron.storage.routing.integration.shardingsphere.jdbc;

import com.xjtu.iron.relational.api.RelationalTemplate;
import com.xjtu.iron.relational.api.statement.SqlStatement;
import com.xjtu.iron.relational.core.DefaultRelationalTemplate;
import com.xjtu.iron.relational.core.connection.DefaultConnectionProvider;
import com.xjtu.iron.relational.core.connection.SingleDataSourceResolver;
import com.xjtu.iron.relational.core.exception.StandardSqlExceptionTranslator;
import com.xjtu.iron.storage.routing.api.key.CompositeShardKey;
import com.xjtu.iron.storage.routing.api.key.ShardKey;
import com.xjtu.iron.storage.routing.api.route.RouteContext;
import com.xjtu.iron.storage.routing.api.route.storage.StorageRoute;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.shardingsphere.driver.api.yaml.YamlShardingSphereDataSourceFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShardingSphereJdbcAdapterIntegrationTest {

    @Test
    void shouldRouteTwoLogicalTablesThroughOneShardingSphereDataSource() throws Exception {
        JdbcDataSource ds0 = physicalDataSource("0");
        JdbcDataSource ds1 = physicalDataSource("1");
        initialize(ds0);
        initialize(ds1);

        try (AutoCloseable logicalDataSource = closeable(shardingSphereDataSource(ds0, ds1))) {
            DataSource dataSource = (DataSource) logicalDataSource;
            RelationalTemplate relational = new DefaultRelationalTemplate(
                    new DefaultConnectionProvider(new SingleDataSourceResolver(dataSource)), new StandardSqlExceptionTranslator());
            ShardingSphereJdbcStorageRouteResolver resolver = new ShardingSphereJdbcStorageRouteResolver();
            ShardingSphereJdbcStorageRouteToSqlRouteBridge bridge = new ShardingSphereJdbcStorageRouteToSqlRouteBridge();
            StorageRoute businessRoute = resolver.resolve(context("business_order", 5L));
            StorageRoute technicalRoute = resolver.resolve(context("iron_idempotency_record", 5L));

            relational.update(bridge.applyRoute(SqlStatement.of("business.insert", "INSERT INTO " + bridge.requireTableName(businessRoute)
                    + " (id, route_id, payload) VALUES (?, ?, ?)", 1L, 5L, "business"), businessRoute));
            relational.update(bridge.applyRoute(SqlStatement.of("idempotency.insert", "INSERT INTO " + bridge.requireTableName(technicalRoute)
                    + " (id, route_id, payload) VALUES (?, ?, ?)", 1L, 5L, "success"), technicalRoute));

            assertThat(count(ds1, "business_order_1")).isEqualTo(1L);
            assertThat(count(ds1, "iron_idempotency_record_1")).isEqualTo(1L);
            assertThat(count(ds0, "business_order_0") + count(ds0, "business_order_1") + count(ds1, "business_order_0")).isZero();
            assertThat(count(ds0, "iron_idempotency_record_0") + count(ds0, "iron_idempotency_record_1")
                    + count(ds1, "iron_idempotency_record_0")).isZero();
        }
    }

    private static DataSource shardingSphereDataSource(DataSource ds0, DataSource ds1) throws Exception {
        Map<String, DataSource> dataSources = new LinkedHashMap<>();
        dataSources.put("ds_0", ds0);
        dataSources.put("ds_1", ds1);
        return YamlShardingSphereDataSourceFactory.createDataSource(dataSources, rules().getBytes(StandardCharsets.UTF_8));
    }

    private static String rules() {
        return """
                databaseName: storage_routing_adapter_test
                rules:
                - !SHARDING
                  tables:
                    business_order:
                      actualDataNodes: ds_${0..1}.business_order_${0..1}
                      databaseStrategy:
                        standard:
                          shardingColumn: route_id
                          shardingAlgorithmName: database_inline
                      tableStrategy:
                        standard:
                          shardingColumn: route_id
                          shardingAlgorithmName: business_table_inline
                    iron_idempotency_record:
                      actualDataNodes: ds_${0..1}.iron_idempotency_record_${0..1}
                      databaseStrategy:
                        standard:
                          shardingColumn: route_id
                          shardingAlgorithmName: database_inline
                      tableStrategy:
                        standard:
                          shardingColumn: route_id
                          shardingAlgorithmName: idempotency_table_inline
                  bindingTables:
                    - business_order,iron_idempotency_record
                  shardingAlgorithms:
                    database_inline:
                      type: INLINE
                      props:
                        algorithm-expression: ds_${route_id % 2}
                    business_table_inline:
                      type: INLINE
                      props:
                        algorithm-expression: business_order_${route_id % 2}
                    idempotency_table_inline:
                      type: INLINE
                      props:
                        algorithm-expression: iron_idempotency_record_${route_id % 2}
                props:
                  sql-show: false
                """;
    }

    private static RouteContext context(String logicalTable, long routeId) {
        return RouteContext.builder().logicalTable(logicalTable)
                .shardKey(CompositeShardKey.of(ShardKey.of("route_id", routeId))).build();
    }

    private static JdbcDataSource physicalDataSource(String suffix) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:ss_adapter_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;IGNORECASE=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private static void initialize(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String logicalTable : new String[]{"business_order", "iron_idempotency_record"}) {
                statement.execute("CREATE TABLE " + logicalTable + "_0 (id BIGINT PRIMARY KEY, route_id BIGINT NOT NULL, payload VARCHAR(64))");
                statement.execute("CREATE TABLE " + logicalTable + "_1 (id BIGINT PRIMARY KEY, route_id BIGINT NOT NULL, payload VARCHAR(64))");
            }
        }
    }

    private static long count(DataSource dataSource, String tableName) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static AutoCloseable closeable(DataSource dataSource) {
        return dataSource instanceof AutoCloseable closeable ? closeable : () -> { };
    }
}
