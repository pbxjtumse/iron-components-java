package com.xjtu.iron.storage.routing.core.resolver;

import com.xjtu.iron.storage.routing.api.PhysicalStorageLocation;
import com.xjtu.iron.storage.routing.api.ShardRouteInfo;
import com.xjtu.iron.storage.routing.api.StorageRoute;
import com.xjtu.iron.storage.routing.api.StorageRouteMode;
import com.xjtu.iron.storage.routing.api.StorageRouteRequest;
import com.xjtu.iron.storage.routing.api.StorageRoutingException;
import com.xjtu.iron.storage.routing.api.resolver.StorageRouteResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultStorageRouteResolverTest {

    @Test
    void shouldCarryRequestMetadataAndTypedResultsThroughTheEntirePipeline() {
        StorageRouteRequest request = StorageRouteRequest.builder().scene("order-create").logicalTable("business_order")
                .shardKeyName("order_id").shardKeyValue("8").attribute("tenantId", "tenant-1").build();
        ShardRouteInfo shardInfo = new ShardRouteInfo(56, 5, 6, 100);
        DefaultStorageRouteResolver resolver = new DefaultStorageRouteResolver(actual -> {
            assertThat(actual).isSameAs(request);
            return shardInfo;
        }, actual -> {
            assertThat(actual).isSameAs(shardInfo);
            return PhysicalStorageLocation.of("db_05", "business_order_56");
        });

        StorageRoute route = resolver.resolve(request);

        assertThat(route.mode()).isEqualTo(StorageRouteMode.DIRECT_DATASOURCE);
        assertThat(route.routeName()).isEqualTo("order-create");
        assertThat(route.logicalTable()).isEqualTo("business_order");
        assertThat(route.shardInfo()).isSameAs(shardInfo);
        assertThat(route.dataSourceKey()).isEqualTo("db_05");
        assertThat(route.tableName()).isEqualTo("business_order_56");
        assertThat(route.shardKeyName()).isEqualTo("order_id");
        assertThat(route.shardKeyValue()).isEqualTo("8");
        assertThat(route.attributes()).containsOnlyKeys("tenantId").containsEntry("tenantId", "tenant-1");
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
        DefaultStorageRouteResolver resolver = new DefaultStorageRouteResolver(new HashShardRouteResolver(10, 10), shard -> null);

        assertThatThrownBy(() -> resolver.resolve(request())).isInstanceOf(StorageRoutingException.class)
                .hasMessageContaining("physicalLocation");
        assertThatThrownBy(() -> resolver.resolve(null)).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("request");
    }

    @Test
    @SuppressWarnings("deprecation")
    void allBuiltInResolversShouldWorkWithBothInterfacePackages() {
        // 编译时同时约束新、旧接口，避免仅名称相同、实现却不能注入同一入口的问题再次出现。
        List<com.xjtu.iron.storage.routing.api.StorageRouteResolver> legacyResolvers = List.of(
                new FixedStorageRouteResolver(StorageRoute.direct("db_05", "order_56")),
                new DefaultStorageRouteResolver(new HashShardRouteResolver(10, 10),
                        shard -> PhysicalStorageLocation.of("db_05", "order_56")),
                ShardIdHashStorageRouteResolver.builder().databaseCount(10).tablesPerDatabase(10).build());

        for (StorageRouteResolver resolver : legacyResolvers) {
            assertThat(resolver.resolve(request()).physicalLocation()).isEqualTo(PhysicalStorageLocation.of("db_05", "order_56"));
        }
    }

    private static StorageRouteRequest request() {
        return StorageRouteRequest.of("order", "order_id", "8");
    }
}
