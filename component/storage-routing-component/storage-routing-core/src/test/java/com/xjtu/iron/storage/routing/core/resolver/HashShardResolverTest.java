package com.xjtu.iron.storage.routing.core.resolver;

import com.xjtu.iron.storage.routing.api.CompositeShardKey;
import com.xjtu.iron.storage.routing.api.PhysicalStorageLocation;
import com.xjtu.iron.storage.routing.api.RouteContext;
import com.xjtu.iron.storage.routing.api.ShardKey;
import com.xjtu.iron.storage.routing.api.ShardRouteInfo;
import com.xjtu.iron.storage.routing.api.StorageRoute;
import com.xjtu.iron.storage.routing.api.TableIndexMode;
import com.xjtu.iron.storage.routing.core.mapping.RouteMappingStrategyFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class HashShardResolverTest {

    @ParameterizedTest
    @MethodSource("singleFieldCompatibilityVectors")
    void typedSingletonsShouldPreservePinnedAssignments(Object value, int expectedShard) {
        CompositeShardKey key = CompositeShardKey.of(ShardKey.of("order_id", value));
        HashShardResolver resolver = new HashShardResolver(10, 10);
        ShardRouteInfo actual = resolver.resolve(key);

        assertThat(actual.shardId()).isEqualTo(expectedShard);
        StorageRoute route = ShardIdHashStorageRouteResolver.builder().databaseCount(10).tablesPerDatabase(10).build()
                .resolve(RouteContext.builder().logicalTable("order").shardKey(key).build());
        assertThat(route.shardInfo()).isEqualTo(actual);
        assertThat(route.context().shardKey()).isEqualTo(key);
    }

    static Stream<Arguments> singleFieldCompatibilityVectors() {
        // 固定期望值，防止外层路由器与底层分片器共用错误实现时相互验证而漏掉回归。
        return Stream.of(Arguments.of("8", 56), Arguments.of((byte) 8, 56), Arguments.of((short) 8, 56),
                Arguments.of(8, 56), Arguments.of(8L, 56), Arguments.of(new BigInteger("8"), 56),
                Arguments.of(new BigDecimal("1.00"), 1), Arguments.of(" 8 ", 20), Arguments.of("", 0),
                Arguments.of("polygenelubricants", 52),
                Arguments.of(UUID.fromString("00000000-0000-0000-0000-000000000008"), 96));
    }

    @Test
    void compositeV1ShouldHavePinnedGlobalAndLocalPhysicalAssignments() {
        CompositeShardKey key = CompositeShardKey.of(ShardKey.of("tenant_id", 42L), ShardKey.of("order_id", "8"));
        RouteContext context = RouteContext.builder().routeName("order-create").logicalTable("order").shardKey(key).build();
        StorageRoute global = ShardIdHashStorageRouteResolver.builder().databaseCount(10).tablesPerDatabase(10).build().resolve(context);
        StorageRoute local = ShardIdHashStorageRouteResolver.builder().databaseCount(10).tablesPerDatabase(10)
                .tableIndexMode(TableIndexMode.LOCAL_TABLE_INDEX).build().resolve(context);

        assertThat(global.context()).isSameAs(context);
        assertThat(global.shardInfo()).isEqualTo(new ShardRouteInfo(42, 4, 2, 100));
        assertThat(local.shardInfo()).isEqualTo(global.shardInfo());
        assertThat(global.location()).isEqualTo(PhysicalStorageLocation.of("db_04", "order_42"));
        assertThat(local.location()).isEqualTo(PhysicalStorageLocation.of("db_04", "order_02"));
    }

    @Test
    void sceneLogicalTableAndAttributesShouldNotAffectShardAssignment() {
        CompositeShardKey key = CompositeShardKey.of(ShardKey.of("tenant_id", 42L), ShardKey.of("order_id", "8"));
        HashShardResolver shardResolver = new HashShardResolver(10, 10);
        DefaultStorageRouteResolver orders = new DefaultStorageRouteResolver(shardResolver,
                new RouteMappingStrategyFactory("db_", "order", 2).create(TableIndexMode.GLOBAL_TABLE_INDEX));
        DefaultStorageRouteResolver outbox = new DefaultStorageRouteResolver(shardResolver,
                new RouteMappingStrategyFactory("db_", "outbox", 2).create(TableIndexMode.GLOBAL_TABLE_INDEX));
        StorageRoute order = orders.resolve(RouteContext.builder().routeName("order-create").logicalTable("order")
                .shardKey(key).attribute("tenant_id", "ignored-metadata").build());
        StorageRoute event = outbox.resolve(RouteContext.builder().routeName("outbox-save").logicalTable("outbox")
                .shardKey(key).attribute("traceId", "different").build());

        assertThat(order.shardInfo()).isEqualTo(event.shardInfo());
        assertThat(order.location()).isEqualTo(PhysicalStorageLocation.of("db_04", "order_42"));
        assertThat(event.location()).isEqualTo(PhysicalStorageLocation.of("db_04", "outbox_42"));
    }
}
