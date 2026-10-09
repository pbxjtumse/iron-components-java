package com.xjtu.iron.storage.routing.integration.shardingsphere.proxy;

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

class ShardingSphereProxyStorageRouteAdapterTest {

    private final ShardingSphereProxyStorageRouteResolver resolver = new ShardingSphereProxyStorageRouteResolver();

    @Test
    void shouldKeepLogicalRouteForShardingSphereProxy() {
        RouteContext context = routeContext("business_order", 1001L);
        StorageRoute route = resolver.resolve(context);
        ShardingSphereProxyStorageRouteToSqlRouteBridge bridge = new ShardingSphereProxyStorageRouteToSqlRouteBridge();

        assertThat(route.mode()).isEqualTo(StorageRouteMode.PROXY);
        assertThat(route.context()).isSameAs(context);
        assertThat(route.physicalLocation()).isNull();
        assertThat(bridge.toSqlRoute(route)).isEqualTo(SqlRoute.defaultRoute());
        assertThat(bridge.requireTableName(route)).isEqualTo("business_order");
    }

    @Test
    void shouldSupportNamedProxyDataSource() {
        StorageRoute route = resolver.resolve(routeContext("business_order", 1001L));
        ShardingSphereProxyStorageRouteToSqlRouteBridge bridge = new ShardingSphereProxyStorageRouteToSqlRouteBridge("orders-proxy");

        assertThat(bridge.toSqlRoute(route)).isEqualTo(SqlRoute.of("orders-proxy"));
    }

    @Test
    void shouldRejectIncompleteOrWrongModeRoutes() {
        assertThatThrownBy(() -> resolver.resolve(RouteContext.builder().shardKey(shardKey(1L)).build()))
                .isInstanceOf(StorageRoutingException.class).hasMessageContaining("logicalTable");
        assertThatThrownBy(() -> resolver.resolve(RouteContext.builder().logicalTable("business_order").build()))
                .isInstanceOf(StorageRoutingException.class).hasMessageContaining("shardKey");

        ShardingSphereProxyStorageRouteToSqlRouteBridge bridge = new ShardingSphereProxyStorageRouteToSqlRouteBridge();
        assertThatThrownBy(() -> bridge.toSqlRoute(StorageRoute.direct("business_order", "db_00", "business_order_00")))
                .isInstanceOf(StorageRoutingException.class).hasMessageContaining("PROXY");
    }

    private static RouteContext routeContext(String logicalTable, long routeId) {
        return RouteContext.builder().logicalTable(logicalTable).shardKey(shardKey(routeId)).build();
    }

    private static CompositeShardKey shardKey(long routeId) {
        return CompositeShardKey.of(ShardKey.of("route_id", routeId));
    }
}
