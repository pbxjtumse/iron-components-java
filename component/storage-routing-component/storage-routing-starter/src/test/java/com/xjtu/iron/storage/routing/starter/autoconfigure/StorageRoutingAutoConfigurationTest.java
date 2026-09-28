package com.xjtu.iron.storage.routing.starter.autoconfigure;

import com.xjtu.iron.storage.routing.api.context.StorageRouteContext;
import com.xjtu.iron.storage.routing.api.context.StorageRouteScope;
import com.xjtu.iron.storage.routing.api.key.CompositeShardKey;
import com.xjtu.iron.storage.routing.api.key.ShardKey;
import com.xjtu.iron.storage.routing.api.resolver.StorageRouteResolver;
import com.xjtu.iron.storage.routing.api.route.PhysicalStorageLocation;
import com.xjtu.iron.storage.routing.api.route.RouteContext;
import com.xjtu.iron.storage.routing.api.route.StorageRouteMode;
import com.xjtu.iron.storage.routing.api.route.storage.StorageRoute;
import com.xjtu.iron.storage.routing.core.context.ThreadLocalStorageRouteContext;
import com.xjtu.iron.storage.routing.integration.relational.StorageRouteToSqlRouteBridge;
import com.xjtu.iron.storage.routing.integration.shardingsphere.jdbc.ShardingSphereJdbcStorageRouteResolver;
import com.xjtu.iron.storage.routing.integration.shardingsphere.jdbc.ShardingSphereJdbcStorageRouteToSqlRouteBridge;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class StorageRoutingAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(StorageRoutingAutoConfiguration.class));

    @Test
    void shouldAutoConfigureContextAndRelationalBridgeByDefault() {
        contextRunner.run(context -> {
            assertThat(context.getBean(StorageRouteContext.class)).isInstanceOf(ThreadLocalStorageRouteContext.class);
            assertThat(context.getBeansOfType(StorageRouteToSqlRouteBridge.class)).hasSize(1);
            assertThat(context.getBeansOfType(StorageRouteResolver.class)).isEmpty();
        });
    }

    @Test
    void shouldAutoConfigureHashResolverWhenTopologyIsEnabled() {
        contextRunner
                .withPropertyValues(
                        "xjtu.iron.storage-routing.resolver.enabled=true",
                        "xjtu.iron.storage-routing.resolver.data-source-prefix=order-db-",
                        "xjtu.iron.storage-routing.resolver.table-prefix=business_order",
                        "xjtu.iron.storage-routing.resolver.database-count=10",
                        "xjtu.iron.storage-routing.resolver.tables-per-database=10",
                        "xjtu.iron.storage-routing.resolver.data-source-index-width=2",
                        "xjtu.iron.storage-routing.resolver.table-index-width=2",
                        "xjtu.iron.storage-routing.resolver.table-index-mode=GLOBAL_TABLE_INDEX")
                .run(context -> {
                    StorageRouteResolver resolver = context.getBean(StorageRouteResolver.class);
                    StorageRoute route = resolver.resolve(RouteContext.builder()
                            .logicalTable("business_order")
                            .shardKey(CompositeShardKey.of(ShardKey.of("order_id", "8")))
                            .build());

                    assertThat(route.location()).isEqualTo(PhysicalStorageLocation.of("order-db-05", "business_order_56"));
                });
    }

    @Test
    void shouldAutoConfigureShardingSphereJdbcAdapter() {
        contextRunner
                .withPropertyValues(
                        "xjtu.iron.storage-routing.mode=SHARDINGSPHERE_JDBC",
                        "xjtu.iron.storage-routing.sharding-sphere-jdbc.data-source-key=orders-sharding")
                .run(context -> {
                    StorageRouteResolver resolver = context.getBean(StorageRouteResolver.class);
                    StorageRouteToSqlRouteBridge bridge = context.getBean(StorageRouteToSqlRouteBridge.class);
                    StorageRoute route = resolver.resolve(RouteContext.builder().logicalTable("business_order")
                            .shardKey(CompositeShardKey.of(ShardKey.of("order_id", 1001L))).build());

                    assertThat(resolver).isInstanceOf(ShardingSphereJdbcStorageRouteResolver.class);
                    assertThat(bridge).isInstanceOf(ShardingSphereJdbcStorageRouteToSqlRouteBridge.class);
                    assertThat(route.mode()).isEqualTo(StorageRouteMode.SHARDINGSPHERE_JDBC);
                    assertThat(bridge.toSqlRoute(route).dataSourceKey()).isEqualTo("orders-sharding");
                    assertThat(bridge.requireTableName(route)).isEqualTo("business_order");
                });
    }

    @Test
    void shouldBackOffWhenUserProvidesContextAndBridge() {
        StorageRouteContext customContext = new ThreadLocalStorageRouteContext();
        StorageRouteToSqlRouteBridge customBridge = new StorageRouteToSqlRouteBridge() {
            @Override
            public com.xjtu.iron.relational.api.statement.SqlRoute toSqlRoute(StorageRoute route) {
                return com.xjtu.iron.relational.api.statement.SqlRoute.of("custom-db");
            }

            @Override
            public String requireTableName(StorageRoute route) {
                return "custom_table";
            }
        };

        contextRunner
                .withBean(StorageRouteContext.class, () -> customContext)
                .withBean(StorageRouteToSqlRouteBridge.class, () -> customBridge)
                .run(context -> {
                    assertThat(context.getBean(StorageRouteContext.class)).isSameAs(customContext);
                    assertThat(context.getBean(StorageRouteToSqlRouteBridge.class)).isSameAs(customBridge);
                });
    }

    @Test
    void autoConfiguredContextShouldSupportRouteScopes() {
        contextRunner.run(context -> {
            StorageRouteContext routeContext = context.getBean(StorageRouteContext.class);
            StorageRoute route = StorageRoute.direct("order", "order-db", "order_01");

            try (StorageRouteScope ignored = routeContext.open(route)) {
                assertThat(routeContext.requireCurrent()).isSameAs(route);
            }

            assertThat(routeContext.current()).isEmpty();
        });
    }
}
