package com.xjtu.iron.storage.routing.core.resolver;

import com.xjtu.iron.storage.routing.api.CompositeShardKey;
import com.xjtu.iron.storage.routing.api.PhysicalStorageLocation;
import com.xjtu.iron.storage.routing.api.RouteContext;
import com.xjtu.iron.storage.routing.api.ShardKey;
import com.xjtu.iron.storage.routing.api.ShardRouteInfo;
import com.xjtu.iron.storage.routing.api.StorageRoute;
import com.xjtu.iron.storage.routing.api.StorageRouteMode;
import com.xjtu.iron.storage.routing.api.StorageRoutingException;
import com.xjtu.iron.storage.routing.api.resolver.StorageRouteResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultStorageRouteResolverTest {

    @Test
    void shouldCarryContextMetadataAndTypedResultsThroughTheEntirePipeline() {
        RouteContext context = RouteContext.builder()
                .routeName("order-create")
                .logicalTable("business_order")
                .shardKey(CompositeShardKey.of(ShardKey.of("order_id", "8")))
                .attribute("tenantId", "tenant-1")
                .build();
        ShardRouteInfo shardInfo = new ShardRouteInfo(56, 5, 6, 100);
        DefaultStorageRouteResolver resolver = new DefaultStorageRouteResolver(actual -> {
            assertThat(actual).isSameAs(context.requireShardKey());
            return shardInfo;
        }, actual -> {
            assertThat(actual).isSameAs(shardInfo);
            return PhysicalStorageLocation.of("db_05", "business_order_56");
        });

        StorageRoute route = resolver.resolve(context);

        assertThat(route.mode()).isEqualTo(StorageRouteMode.DIRECT_DATASOURCE);
        assertThat(route.context()).isSameAs(context);
        assertThat(route.routeName()).isEqualTo("order-create");
        assertThat(route.logicalTable()).isEqualTo("business_order");
        assertThat(route.shardInfo()).isSameAs(shardInfo);
        assertThat(route.dataSourceKey()).isEqualTo("db_05");
        assertThat(route.tableName()).isEqualTo("business_order_56");
        assertThat(route.context().requireShardKey().singleKey()).isEqualTo(ShardKey.of("order_id", "8"));
        assertThat(route.context().attributes()).containsOnlyKeys("tenantId").containsEntry("tenantId", "tenant-1");
    }

    @Test
    void shouldStopBeforeMappingWhenShardResolverReturnsNoResult() {
        AtomicBoolean mapped = new AtomicBoolean();
        DefaultStorageRouteResolver resolver = new DefaultStorageRouteResolver(request -> null, shard -> {
            mapped.set(true);
            return PhysicalStorageLocation.of("db_05", "order_56");
        });

        assertThatThrownBy(() -> resolver.resolve(request())).isInstanceOf(StorageRoutingException.class)
                .hasMessageContaining("shardInfo");
        assertThat(mapped).isFalse();
    }

    @Test
    void shouldRejectMissingPhysicalLocationInsteadOfReturningUnusableRoute() {
        DefaultStorageRouteResolver resolver = new DefaultStorageRouteResolver(new HashShardResolver(10, 10), shard -> null);

        assertThatThrownBy(() -> resolver.resolve(request())).isInstanceOf(StorageRoutingException.class)
                .hasMessageContaining("physicalLocation");
        assertThatThrownBy(() -> resolver.resolve((RouteContext) null)).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("context");
    }

    @Test
    void allBuiltInResolversShouldWorkWithTheUnifiedInterface() {
        List<StorageRouteResolver> resolvers = List.of(
                new FixedStorageRouteResolver(StorageRoute.direct("db_05", "order_56")),
                new DefaultStorageRouteResolver(new HashShardResolver(10, 10),
                        shard -> PhysicalStorageLocation.of("db_05", "order_56")),
                ShardIdHashStorageRouteResolver.builder().databaseCount(10).tablesPerDatabase(10).build());

        for (StorageRouteResolver resolver : resolvers) {
            assertThat(resolver.resolve(request()).physicalLocation())
                    .isEqualTo(PhysicalStorageLocation.of("db_05", "order_56"));
        }
    }

    private static RouteContext request() {
        return context("order", "order_id", "8");
    }

    private static RouteContext context(String logicalTable, String shardKeyName, Object shardKeyValue) {
        return RouteContext.builder()
                .logicalTable(logicalTable)
                .shardKey(CompositeShardKey.of(ShardKey.of(shardKeyName, shardKeyValue)))
                .build();
    }
}
