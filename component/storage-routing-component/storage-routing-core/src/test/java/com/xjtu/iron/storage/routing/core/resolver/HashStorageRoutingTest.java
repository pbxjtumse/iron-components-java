package com.xjtu.iron.storage.routing.core.resolver;

import com.xjtu.iron.storage.routing.api.*;
import com.xjtu.iron.storage.routing.api.mapping.RouteMappingStrategy;
import com.xjtu.iron.storage.routing.core.mapping.RouteMappingStrategyFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HashStorageRoutingTest {

    @ParameterizedTest
    @CsvSource({"8, 56, 5, 6, order_56, order_06", "a, 97, 9, 7, order_97, order_07",
            "polygenelubricants, 52, 5, 2, order_52, order_02"})
    void modelRefactoringShouldPreserveExistingShardAndTableAssignments(String key, int shardId, int databaseIndex,
            int localTableIndex, String globalTable, String localTable) {
        StorageRouteRequest request = StorageRouteRequest.of("order", "order_id", key);
        StorageRoute global = resolver(TableIndexMode.GLOBAL_TABLE_INDEX).resolve(request);
        StorageRoute local = resolver(TableIndexMode.LOCAL_TABLE_INDEX).resolve(request);

        assertThat(global.shardInfo()).isEqualTo(new ShardRouteInfo(shardId, databaseIndex, localTableIndex, 100));
        assertThat(local.shardInfo()).isEqualTo(global.shardInfo());
        assertThat(global.dataSourceKey()).isEqualTo("db_0" + databaseIndex);
        assertThat(local.dataSourceKey()).isEqualTo(global.dataSourceKey());
        assertThat(global.tableName()).isEqualTo(globalTable);
        assertThat(local.tableName()).isEqualTo(localTable);
        assertThat(global.logicalTable()).isEqualTo("order");
        assertThat(global.attributes()).isEmpty();
    }

    @Test
    void sameShardShouldMapBusinessAndTechnicalRecordsToDifferentTablesInTheSameDatabase() {
        ShardRouteInfo shard = new HashShardRouteResolver(10, 10).resolve(StorageRouteRequest.of("order", "order_id", "8"));
        RouteMappingStrategy orders = new RouteMappingStrategyFactory("db_", "order", 2).create(TableIndexMode.GLOBAL_TABLE_INDEX);
        RouteMappingStrategy idempotency = new RouteMappingStrategyFactory("db_", "idempotency", 2).create(TableIndexMode.GLOBAL_TABLE_INDEX);

        assertThat(orders.map(shard)).isEqualTo(PhysicalStorageLocation.of("db_05", "order_56"));
        assertThat(idempotency.map(shard)).isEqualTo(PhysicalStorageLocation.of("db_05", "idempotency_56"));
    }

    @Test
    void localTableIndexShouldSupportTenDatabasesWithTenOrOneHundredTablesPerDatabase() {
        RouteMappingStrategy mapping = new RouteMappingStrategyFactory("db_", "order", 2)
                .create(TableIndexMode.LOCAL_TABLE_INDEX);

        assertThat(mapping.map(new ShardRouteInfo(99, 9, 9, 100)))
                .isEqualTo(PhysicalStorageLocation.of("db_09", "order_09"));
        assertThat(mapping.map(new ShardRouteInfo(999, 9, 99, 1000)))
                .isEqualTo(PhysicalStorageLocation.of("db_09", "order_99"));
    }

    @Test
    void shouldRejectInvalidTopologyBeforeHandlingRequests() {
        assertThatThrownBy(() -> new HashShardRouteResolver(0, 10)).isInstanceOf(StorageRoutingException.class);
        assertThatThrownBy(() -> new HashShardRouteResolver(10, -1)).isInstanceOf(StorageRoutingException.class);
        assertThatThrownBy(() -> ShardIdHashStorageRouteResolver.builder().databaseCount(65_536).tablesPerDatabase(65_536).build())
                .isInstanceOf(StorageRoutingException.class).hasMessageContaining("totalShardCount");
    }

    @ParameterizedTest
    @EnumSource(TableIndexMode.class)
    void shouldValidateMappingConfigurationBeforeProducingLocation(TableIndexMode mode) {
        assertThatThrownBy(() -> new RouteMappingStrategyFactory("db_", "order", 0).create(mode))
                .isInstanceOf(StorageRoutingException.class).hasMessageContaining("tableIndexWidth");
        assertThatThrownBy(() -> new RouteMappingStrategyFactory(" ", "order", 2).create(mode))
                .isInstanceOf(StorageRoutingException.class).hasMessageContaining("Prefix");
    }

    @Test
    void generatedPhysicalNamesShouldNotDependOnDefaultLocale() {
        Locale previous = Locale.getDefault(Locale.Category.FORMAT);
        try {
            Locale.setDefault(Locale.Category.FORMAT, Locale.forLanguageTag("ar-EG"));
            StorageRoute route = resolver(TableIndexMode.LOCAL_TABLE_INDEX).resolve(StorageRouteRequest.of("order", "order_id", "8"));
            assertThat(route.physicalLocation()).isEqualTo(PhysicalStorageLocation.of("db_05", "order_06"));
        } finally {
            Locale.setDefault(Locale.Category.FORMAT, previous);
        }
    }

    private static ShardIdHashStorageRouteResolver resolver(TableIndexMode mode) {
        return ShardIdHashStorageRouteResolver.builder().databaseCount(10).tablesPerDatabase(10).tableIndexMode(mode).build();
    }
}
