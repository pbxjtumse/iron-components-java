package com.xjtu.iron.storage.routing.core.resolver;

import com.xjtu.iron.storage.routing.api.StorageRoute;
import com.xjtu.iron.storage.routing.api.StorageRouteRequest;
import com.xjtu.iron.storage.routing.api.StorageRoutingException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HashStorageRouteResolverTest {

    @Test
    void shouldResolveDataSourceAndPhysicalTableByShardKey() {
        HashStorageRouteResolver resolver = HashStorageRouteResolver.builder()
                .dataSourcePrefix("order-db-")
                .tablePrefix("business_order")
                .dataSourceCount(10)
                .tableCount(100)
                .dataSourceIndexWidth(1)
                .tableIndexWidth(3)
                .build();

        StorageRouteRequest request = StorageRouteRequest.builder()
                .scene("create-order")
                .logicalTable("business_order")
                .shardKeyName("order_id")
                .shardKeyValue("order-10001")
                .build();

        StorageRoute route = resolver.resolve(request);

        assertThat(route.routeName()).isEqualTo("create-order");
        assertThat(route.dataSourceKey()).startsWith("order-db-");
        assertThat(route.tableName()).startsWith("business_order_");
        assertThat(route.shardKeyName()).isEqualTo("order_id");
        assertThat(route.shardKeyValue()).isEqualTo("order-10001");
        assertThat(route.attribute("logicalTable")).isEqualTo("business_order");
        assertThat(route.attribute("dataSourceIndex")).isInstanceOf(Integer.class);
        assertThat(route.attribute("tableIndex")).isInstanceOf(Integer.class);
    }

    @Test
    void shouldUseLogicalTableAsTablePrefixWhenTablePrefixIsNotProvided() {
        HashStorageRouteResolver resolver = HashStorageRouteResolver.builder()
                .dataSourcePrefix("order-db-")
                .dataSourceCount(2)
                .tableCount(4)
                .build();

        StorageRoute route = resolver.resolve(StorageRouteRequest.of("iron_idempotency_record", "order_id", "order-10002"));

        assertThat(route.tableName()).startsWith("iron_idempotency_record_");
    }

    @Test
    void shouldRejectInvalidShardConfig() {
        assertThatThrownBy(() -> HashStorageRouteResolver.builder()
                .dataSourcePrefix("order-db-")
                .dataSourceCount(0)
                .tableCount(100)
                .build())
                .isInstanceOf(StorageRoutingException.class)
                .hasMessageContaining("dataSourceCount");
    }
}
