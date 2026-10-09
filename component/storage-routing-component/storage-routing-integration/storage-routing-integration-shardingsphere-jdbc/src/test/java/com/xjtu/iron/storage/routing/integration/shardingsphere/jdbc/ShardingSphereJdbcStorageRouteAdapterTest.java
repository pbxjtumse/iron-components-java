package com.xjtu.iron.storage.routing.integration.shardingsphere.jdbc;

import com.xjtu.iron.relational.api.statement.SqlRoute;
import com.xjtu.iron.storage.routing.api.exception.StorageRoutingException;
import com.xjtu.iron.storage.routing.api.key.CompositeShardKey;
import com.xjtu.iron.storage.routing.api.key.ShardKey;
import com.xjtu.iron.storage.routing.api.route.RouteContext;
import com.xjtu.iron.storage.routing.api.route.StorageRouteMode;
import com.xjtu.iron.storage.routing.api.route.storage.StorageRoute;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShardingSphereJdbcStorageRouteAdapterTest {

    private final ShardingSphereJdbcStorageRouteResolver resolver = new ShardingSphereJdbcStorageRouteResolver();

    @Test
    void shouldKeepLogicalRouteForShardingSphere() {
        RouteContext context = routeContext("business_order", 1001L);
        StorageRoute route = resolver.resolve(context);
        ShardingSphereJdbcStorageRouteToSqlRouteBridge bridge = new ShardingSphereJdbcStorageRouteToSqlRouteBridge();

        assertThat(route.mode()).isEqualTo(StorageRouteMode.SHARDINGSPHERE_JDBC);
        assertThat(route.context()).isSameAs(context);
        assertThat(route.physicalLocation()).isNull();
        assertThat(bridge.toSqlRoute(route)).isEqualTo(SqlRoute.defaultRoute());
        assertThat(bridge.requireTableName(route)).isEqualTo("business_order");
    }

    @Test
    void shouldSupportNamedLogicalDataSource() {
        StorageRoute route = resolver.resolve(routeContext("business_order", 1001L));
        ShardingSphereJdbcStorageRouteToSqlRouteBridge bridge = new ShardingSphereJdbcStorageRouteToSqlRouteBridge("orders-sharding");

        assertThat(bridge.toSqlRoute(route)).isEqualTo(SqlRoute.of("orders-sharding"));
    }

    @Test
    void shouldRejectIncompleteOrWrongModeRoutes() {
        assertThatThrownBy(() -> resolver.resolve(RouteContext.builder().shardKey(shardKey(1L)).build()))
                .isInstanceOf(StorageRoutingException.class).hasMessageContaining("logicalTable");
        assertThatThrownBy(() -> resolver.resolve(RouteContext.builder().logicalTable("business_order").build()))
                .isInstanceOf(StorageRoutingException.class).hasMessageContaining("shardKey");

        ShardingSphereJdbcStorageRouteToSqlRouteBridge bridge = new ShardingSphereJdbcStorageRouteToSqlRouteBridge();
        assertThatThrownBy(() -> bridge.toSqlRoute(StorageRoute.direct("business_order", "db_00", "business_order_00")))
                .isInstanceOf(StorageRoutingException.class).hasMessageContaining("SHARDINGSPHERE_JDBC");
    }

    private static RouteContext routeContext(String logicalTable, long routeId) {
        return RouteContext.builder().logicalTable(logicalTable).shardKey(shardKey(routeId)).build();
    }

    private static CompositeShardKey shardKey(long routeId) {
        return CompositeShardKey.of(ShardKey.of("route_id", routeId));
    }
}
