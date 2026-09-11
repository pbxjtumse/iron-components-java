package com.xjtu.iron.storage.routing.core;

import com.xjtu.iron.storage.routing.api.CompositeShardKey;
import com.xjtu.iron.storage.routing.api.PhysicalStorageLocation;
import com.xjtu.iron.storage.routing.api.RouteContext;
import com.xjtu.iron.storage.routing.api.ShardKey;
import com.xjtu.iron.storage.routing.api.ShardRouteInfo;
import com.xjtu.iron.storage.routing.api.StorageRoute;
import com.xjtu.iron.storage.routing.api.StorageRouteRequest;
import com.xjtu.iron.storage.routing.api.StorageRoutingException;
import com.xjtu.iron.storage.routing.core.resolver.DefaultStorageRouteResolver;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteCompositionTest {

    @Test
    void resolverShouldComposeContextShardAndLocationWithoutDroppingCompositeFields() {
        CompositeShardKey key = CompositeShardKey.of(ShardKey.of("tenant_id", 42L), ShardKey.of("order_id", "8"));
        RouteContext context = RouteContext.builder().routeName("order-create").logicalTable("business_order")
                .shardKey(key).attribute("traceId", "trace-1").build();
        ShardRouteInfo shard = new ShardRouteInfo(56, 5, 6, 100);
        PhysicalStorageLocation location = PhysicalStorageLocation.of("db_05", "business_order_56");
        DefaultStorageRouteResolver resolver = new DefaultStorageRouteResolver(actual -> {
            // 计算器只能看到分片键，不能依赖场景、逻辑表或扩展元数据。
            assertThat(actual).isSameAs(key);
            return shard;
        }, actual -> {
            assertThat(actual).isSameAs(shard);
            return location;
        });

        StorageRoute route = resolver.resolve(context);

        assertThat(route.context()).isSameAs(context);
        assertThat(route.context().shardKey().keys()).containsExactlyElementsOf(key.keys());
        assertThat(route.shardInfo()).isSameAs(shard);
        assertThat(route.location()).isEqualTo(location);
        assertThat(route.physicalLocation()).isSameAs(route.location());
        assertThat(route.routeName()).isEqualTo(context.routeName());
        assertThat(route.logicalTable()).isEqualTo(context.logicalTable());
        assertThat(route.attributes()).isSameAs(context.attributes());
        assertThat(route.attribute("traceId")).isEqualTo("trace-1");
    }

    @Test
    @SuppressWarnings("deprecation")
    void oldSingleFieldGettersShouldFailForCompositeKeysInsteadOfTakingTheFirstField() {
        StorageRoute route = StorageRoute.builder().context(RouteContext.builder()
                        .shardKey(CompositeShardKey.of(ShardKey.of("tenant_id", 42L), ShardKey.of("order_id", "8"))).build())
                .location(PhysicalStorageLocation.of("db_05", "order_56")).build();

        assertThatThrownBy(route::shardKeyName).isInstanceOf(StorageRoutingException.class);
        assertThatThrownBy(route::shardKeyValue).isInstanceOf(StorageRoutingException.class);
    }

    @Test
    @SuppressWarnings("deprecation")
    void contextAndLegacyBuilderMetadataShouldNotCreateCompetingSourcesOfTruth() {
        RouteContext context = RouteContext.builder().routeName("order-create").build();

        assertThatThrownBy(() -> StorageRoute.builder().context(context).routeName("other")
                .location(PhysicalStorageLocation.of("db", "orders")).build())
                .isInstanceOf(StorageRoutingException.class).hasMessageContaining("mixed");
        assertThatThrownBy(() -> StorageRoute.builder().attribute("tenant", "other").context(context)
                .location(PhysicalStorageLocation.of("db", "orders")).build())
                .isInstanceOf(StorageRoutingException.class).hasMessageContaining("mixed");
    }

    @Test
    void contextShouldSnapshotAttributesAcrossInputMapAndBuilderMutations() {
        Map<String, Object> attributes = new LinkedHashMap<>(Map.of("traceId", "trace-1"));
        RouteContext.Builder builder = RouteContext.builder().attributes(attributes);
        attributes.put("traceId", "trace-2");
        RouteContext context = builder.build();
        builder.attribute("traceId", "trace-3");

        assertThat(context.attribute("traceId")).isEqualTo("trace-1");
        assertThatThrownBy(() -> context.attributes().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(builder.build().attribute("traceId")).isEqualTo("trace-3");
    }

    @Test
    void missingKeysShouldBeAllowedForFixedRoutesButRejectedBeforeShardCalculation() {
        AtomicBoolean calculated = new AtomicBoolean();
        DefaultStorageRouteResolver resolver = new DefaultStorageRouteResolver(key -> {
            calculated.set(true);
            return new ShardRouteInfo(0, 0, 0, 1);
        }, shard -> PhysicalStorageLocation.of("db", "orders"));

        assertThatThrownBy(() -> resolver.resolve(RouteContext.builder().build()))
                .isInstanceOf(StorageRoutingException.class).hasMessageContaining("shardKey");
        assertThat(calculated).isFalse();
        assertThat(StorageRoute.direct("db", "orders").context()).isNotNull();
        assertThat(StorageRoute.direct("db", "orders").context().shardKey()).isNull();
    }

    @Test
    @SuppressWarnings("deprecation")
    void legacyRequestShouldConvertOnceAndRetainItsSingleTypedValue() {
        StorageRouteRequest request = StorageRouteRequest.builder().scene("order-create").logicalTable("order")
                .shardKeyName("order_id").shardKeyValue(8L).attribute("traceId", "trace-1").build();

        assertThat(request.toContext()).isSameAs(request.toContext());
        assertThat(request.toContext().shardKey().keys()).containsExactly(ShardKey.of("order_id", 8L));
        assertThat(request.toContext().routeName()).isEqualTo("order-create");
        assertThat(request.toContext().logicalTable()).isEqualTo("order");
        assertThat(request.shardKeyValue()).isEqualTo(8L);
        assertThat(request.attribute("traceId")).isEqualTo("trace-1");
    }
}
