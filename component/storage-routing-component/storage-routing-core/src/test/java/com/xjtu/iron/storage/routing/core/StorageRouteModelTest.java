package com.xjtu.iron.storage.routing.core;

import com.xjtu.iron.storage.routing.api.PhysicalStorageLocation;
import com.xjtu.iron.storage.routing.api.ShardRouteInfo;
import com.xjtu.iron.storage.routing.api.StorageRoute;
import com.xjtu.iron.storage.routing.api.StorageRouteMode;
import com.xjtu.iron.storage.routing.api.StorageRoutingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageRouteModelTest {

    @Test
    void shouldKeepLogicalTableSeparateFromPhysicalTable() {
        StorageRoute route = StorageRoute.direct(" business_order ", " db_05 ", " business_order_56 ");

        assertThat(route.logicalTable()).isEqualTo("business_order");
        assertThat(route.physicalLocation()).isEqualTo(PhysicalStorageLocation.of("db_05", "business_order_56"));
        assertThat(route.dataSourceKey()).isEqualTo("db_05");
        assertThat(route.tableName()).isEqualTo("business_order_56");
        assertThat(route.shardInfo()).isNull();
        assertThat(StorageRoute.direct("db_05", "business_order_56").logicalTable()).isNull();
    }

    @Test
    void legacyBuilderShouldAcceptEitherFieldOrder() {
        StorageRoute dataSourceFirst = StorageRoute.builder().dataSourceKey("db_05").tableName("order_56").build();
        StorageRoute tableFirst = StorageRoute.builder().tableName("order_56").dataSourceKey("db_05").build();

        assertThat(dataSourceFirst.physicalLocation()).isEqualTo(tableFirst.physicalLocation());
    }

    @Test
    void mixedBuilderMethodsShouldKeepOnePhysicalLocation() {
        StorageRoute route = StorageRoute.builder()
                .physicalLocation(PhysicalStorageLocation.of("db_05", "order_56"))
                .tableName("idempotency_56")
                .build();
        StorageRoute replacement = StorageRoute.builder().dataSourceKey("old-db").tableName("old-table")
                .physicalLocation(PhysicalStorageLocation.of("db_05", "order_56")).build();

        assertThat(route.physicalLocation()).isEqualTo(PhysicalStorageLocation.of("db_05", "idempotency_56"));
        assertThat(replacement.dataSourceKey()).isEqualTo("db_05");
        assertThat(replacement.tableName()).isEqualTo("order_56");
    }

    @Test
    void shouldRejectIncompleteDirectLocationsAtBuildTime() {
        assertThatThrownBy(() -> StorageRoute.builder().build()).isInstanceOf(StorageRoutingException.class);
        assertThatThrownBy(() -> StorageRoute.builder().tableName("order_56").build())
                .isInstanceOf(StorageRoutingException.class).hasMessageContaining("dataSourceKey");
        assertThatThrownBy(() -> StorageRoute.builder().dataSourceKey("db_05").build())
                .isInstanceOf(StorageRoutingException.class).hasMessageContaining("tableName");
        assertThatThrownBy(() -> PhysicalStorageLocation.of(" ", "order_56"))
                .isInstanceOf(StorageRoutingException.class);
        assertThatThrownBy(() -> PhysicalStorageLocation.of("db_05", null))
                .isInstanceOf(StorageRoutingException.class);
    }

    @Test
    void shouldSnapshotAttributesWithoutMutatingCallerOrPreviousResult() {
        Map<String, Object> source = new LinkedHashMap<>(Map.of("tenantId", "tenant-1"));
        StorageRoute.Builder builder = StorageRoute.builder().dataSourceKey("db_05").tableName("order_56")
                .attributes(source).attribute("traceId", "trace-1");
        source.put("tenantId", "tenant-2");
        StorageRoute route = builder.build();
        builder.attribute("traceId", "trace-2");

        assertThat(source).doesNotContainKey("traceId");
        assertThat(route.attributes()).containsEntry("tenantId", "tenant-1").containsEntry("traceId", "trace-1");
        assertThatThrownBy(() -> route.attributes().put("tenantId", "changed"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(builder.build().attribute("traceId")).isEqualTo("trace-2");
    }

    @ParameterizedTest
    @EnumSource(value = StorageRouteMode.class, names = {"SHARDINGSPHERE_JDBC", "PROXY"})
    void delegatedModesShouldNotPretendLogicalTableIsPhysical(StorageRouteMode mode) {
        StorageRoute route = StorageRoute.builder().mode(mode).logicalTable("business_order").build();

        assertThat(route.logicalTable()).isEqualTo("business_order");
        assertThat(route.physicalLocation()).isNull();
        assertThat(route.dataSourceKey()).isNull();
        assertThat(route.tableName()).isNull();
    }

    @Test
    void shouldRejectImpossibleShardCoordinates() {
        assertThatThrownBy(() -> new ShardRouteInfo(100, 0, 0, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShardRouteInfo(0, 100, 0, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShardRouteInfo(0, 0, 100, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShardRouteInfo(-1, 0, 0, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShardRouteInfo(0, 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
