package com.xjtu.iron.idempotent.integration.storage.routing;

import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryQuery;
import com.xjtu.iron.idempotent.api.storage.IdempotencyStorageContext;
import com.xjtu.iron.idempotent.provider.jdbc.routing.IdempotencyJdbcRoute;
import com.xjtu.iron.storage.routing.api.context.StorageRouteContext;
import com.xjtu.iron.storage.routing.api.context.StorageRouteScope;
import com.xjtu.iron.storage.routing.api.exception.StorageRoutingException;
import com.xjtu.iron.storage.routing.api.key.CompositeShardKey;
import com.xjtu.iron.storage.routing.api.key.ShardKey;
import com.xjtu.iron.storage.routing.api.mapping.RouteMappingStrategy;
import com.xjtu.iron.storage.routing.api.resolver.StorageRouteResolver;
import com.xjtu.iron.storage.routing.api.route.PhysicalStorageLocation;
import com.xjtu.iron.storage.routing.api.route.RouteContext;
import com.xjtu.iron.storage.routing.api.route.ShardRouteInfo;
import com.xjtu.iron.storage.routing.api.route.StorageRoute;
import com.xjtu.iron.storage.routing.integration.relational.DefaultStorageRouteToSqlRouteBridge;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageRoutingIdempotencyJdbcRouteResolverTest {

    @Test
    void boundRouteMustNotBeRemappedIntoAnotherDatabase() {
        TestStorageRouteContext current = new TestStorageRouteContext();
        current.set(sharded(RouteContext.builder().logicalTable("business").build(), new ShardRouteInfo(17, 1, 7, 100), "db_01", "business_07"));
        var resolver = resolver(input -> { throw new AssertionError("must not rehash"); }, current,
                shard -> PhysicalStorageLocation.of("other_db_01", "idempotency_07"));
        assertThatThrownBy(() -> resolver.resolvePoint(IdempotencyStorageContext.of("default", 0), "default", "key"))
                .isInstanceOf(StorageRoutingException.class).hasMessageContaining("same dataSourceKey");
        assertThatThrownBy(() -> resolver.resolveRecoveryRoutes(new IdempotencyRecoveryQuery("default", "default", 0, Instant.now(), 10)))
                .isInstanceOf(StorageRoutingException.class).hasMessageContaining("same dataSourceKey");
    }

    @Test
    void shouldUseIdempotencyKeyAndRemapGlobalResolverLocationToIdempotencyTable() {
        AtomicReference<RouteContext> captured = new AtomicReference<>();
        ShardRouteInfo shardInfo = new ShardRouteInfo(17, 3, 5, 100);
        StorageRouteResolver routeResolver = context -> {
            captured.set(context);
            // 故意返回 business 表；Idempotency integration 必须只复用 shardInfo，不能直接复用这个 physical table。
            return sharded(context, shardInfo, "business-db-03", "business_order_017");
        };

        RouteMappingStrategy idempotencyMapping = shard -> {
            assertThat(shard).isSameAs(shardInfo);
            return PhysicalStorageLocation.of("idempotency-db-03", "iron_idempotency_record_017");
        };

        StorageRoutingIdempotencyJdbcRouteResolver resolver = resolver(
                routeResolver, new TestStorageRouteContext(), idempotencyMapping);
        IdempotencyJdbcRoute route = resolver.resolvePoint(
                IdempotencyStorageContext.of("message-consume", 417), "message", "MSG-10001");

        assertThat(route.dataSourceKey()).isEqualTo("idempotency-db-03");
        assertThat(route.tableName()).isEqualTo("iron_idempotency_record_017");
        assertThat(captured.get().routeName()).isEqualTo("message-consume");
        assertThat(captured.get().logicalTable()).isEqualTo("iron_idempotency_record");
        assertThat(captured.get().requireShardKey().singleKey().name()).isEqualTo(DefaultIdempotencyRouteContextFactory.IDEMPOTENCY_KEY_FIELD);
        assertThat(captured.get().requireShardKey().singleKey().value().canonicalText()).isEqualTo("MSG-10001");
        assertThat(captured.get().attribute("idempotency.scanBucket")).isEqualTo(417);
    }

    @Test
    void shouldReuseBoundBusinessCompositeShardKeyAndShardInfoWithoutRehashing() {
        CompositeShardKey businessShardKey = CompositeShardKey.of(
                ShardKey.of("tenant_id", 12L),
                ShardKey.of("order_id", 90001L));
        ShardRouteInfo shardInfo = new ShardRouteInfo(31, 2, 7, 100);
        RouteContext businessContext = RouteContext.builder()
                .routeName("order")
                .logicalTable("business_order")
                .shardKey(businessShardKey)
                .build();

        TestStorageRouteContext current = new TestStorageRouteContext();
        current.set(sharded(businessContext, shardInfo, "order-db-02", "business_order_031"));

        AtomicReference<RouteContext> unexpectedResolverCall = new AtomicReference<>();
        StorageRouteResolver routeResolver = context -> {
            unexpectedResolverCall.set(context);
            return sharded(context, shardInfo, "order-db-02", "business_order_031");
        };
        RouteMappingStrategy idempotencyMapping = actual -> {
            assertThat(actual).isSameAs(shardInfo);
            return PhysicalStorageLocation.of("order-db-02", "iron_idempotency_record_031");
        };

        StorageRoutingIdempotencyJdbcRouteResolver resolver = resolver(routeResolver, current, idempotencyMapping);
        IdempotencyJdbcRoute route = resolver.resolvePoint(
                IdempotencyStorageContext.of("order-write", 18), "order", "CREATE-90001");

        assertThat(route).isEqualTo(IdempotencyJdbcRoute.of("order-db-02", "iron_idempotency_record_031"));
        assertThat(unexpectedResolverCall.get()).isNull();
    }

    @Test
    void recoveryScanShouldFailFastWhenShardedResolverNeedsKeyButNoShardScopeIsBound() {
        StorageRouteResolver shardRequiredResolver = context -> {
            context.requireShardKey();
            return sharded(context, new ShardRouteInfo(1, 0, 1, 10), "db-00", "business_01");
        };
        StorageRoutingIdempotencyJdbcRouteResolver resolver = resolver(
                shardRequiredResolver,
                new TestStorageRouteContext(),
                shard -> PhysicalStorageLocation.of("db-00", "iron_idempotency_record_01"));

        IdempotencyRecoveryQuery query = new IdempotencyRecoveryQuery(
                "message-consume", "message", 7, Instant.parse("2026-09-13T00:00:00Z"), 100);

        assertThatThrownBy(() -> resolver.resolveRecoveryRoutes(query))
                .isInstanceOf(StorageRoutingException.class)
                .hasMessageContaining("external Reliable Task must enumerate a physical shard")
                .hasMessageContaining("scanBucket=7");
    }

    @Test
    void recoveryScanShouldReuseBoundShardInfoAndMapToIdempotencyTable() {
        CompositeShardKey shardKey = CompositeShardKey.of(ShardKey.of("merchant_id", 3001L));
        ShardRouteInfo shardInfo = new ShardRouteInfo(5, 3, 5, 100);
        TestStorageRouteContext current = new TestStorageRouteContext();
        current.set(sharded(
                RouteContext.builder().routeName("payment").logicalTable("payment_order").shardKey(shardKey).build(),
                shardInfo,
                "payment-db-03",
                "payment_order_005"));

        StorageRouteResolver routeResolver = context -> {
            throw new AssertionError("bound shardInfo should make recovery remapping independent from re-resolving shard key");
        };
        RouteMappingStrategy idempotencyMapping = actual -> {
            assertThat(actual).isSameAs(shardInfo);
            return PhysicalStorageLocation.of("payment-db-03", "iron_idempotency_record_005");
        };

        StorageRoutingIdempotencyJdbcRouteResolver resolver = resolver(routeResolver, current, idempotencyMapping);
        IdempotencyRecoveryQuery query = new IdempotencyRecoveryQuery(
                "payment", "payment", 9, Instant.parse("2026-09-13T00:00:00Z"), 100);

        assertThat(resolver.resolveRecoveryRoutes(query))
                .containsExactly(IdempotencyJdbcRoute.of("payment-db-03", "iron_idempotency_record_005"));
    }

    @Test
    void directResolverWithoutShardInfoShouldReuseOnlyDatasourceNotBusinessTable() {
        StorageRouteResolver directResolver = context -> StorageRoute.builder()
                .context(context)
                .location(PhysicalStorageLocation.of("single-db", "business_order"))
                .build();

        StorageRoutingIdempotencyJdbcRouteResolver resolver = resolver(
                directResolver,
                new TestStorageRouteContext(),
                shard -> {
                    throw new AssertionError("direct route without shardInfo must not invoke sharded mapping");
                });

        IdempotencyJdbcRoute route = resolver.resolvePoint(
                IdempotencyStorageContext.of("default", 0), "default", "IDEMP-1");

        assertThat(route).isEqualTo(IdempotencyJdbcRoute.of("single-db", "iron_idempotency_record"));
    }

    @Test
    void boundDirectBusinessRouteShouldNotLeakBusinessTableIntoIdempotencySql() {
        TestStorageRouteContext current = new TestStorageRouteContext();
        current.set(StorageRoute.builder()
                .context(RouteContext.builder().routeName("order").logicalTable("business_order").build())
                .location(PhysicalStorageLocation.of("order-db", "business_order"))
                .build());

        StorageRouteResolver routeResolver = context -> {
            throw new AssertionError("bound direct route should not require another routing resolution");
        };
        StorageRoutingIdempotencyJdbcRouteResolver resolver = resolver(
                routeResolver,
                current,
                shard -> {
                    throw new AssertionError("direct route without shardInfo must not invoke sharded mapping");
                });

        IdempotencyJdbcRoute route = resolver.resolvePoint(
                IdempotencyStorageContext.of("order-write", 0), "order", "CREATE-1");

        assertThat(route).isEqualTo(IdempotencyJdbcRoute.of("order-db", "iron_idempotency_record"));
    }

    private StorageRoutingIdempotencyJdbcRouteResolver resolver(
            StorageRouteResolver routeResolver,
            StorageRouteContext routeContext,
            RouteMappingStrategy idempotencyMapping
    ) {
        return new StorageRoutingIdempotencyJdbcRouteResolver(
                "iron_idempotency_record",
                "iron_idempotency_record",
                routeResolver,
                routeContext,
                idempotencyMapping,
                new DefaultStorageRouteToSqlRouteBridge());
    }

    private static StorageRoute sharded(
            RouteContext context,
            ShardRouteInfo shardInfo,
            String dataSourceKey,
            String tableName
    ) {
        return StorageRoute.builder()
                .context(context)
                .shardInfo(shardInfo)
                .location(PhysicalStorageLocation.of(dataSourceKey, tableName))
                .build();
    }

    private static final class TestStorageRouteContext implements StorageRouteContext {
        private StorageRoute current;

        @Override
        public Optional<StorageRoute> current() {
            return Optional.ofNullable(current);
        }

        @Override
        public StorageRouteScope open(StorageRoute route) {
            StorageRoute previous = current;
            current = route;
            return () -> current = previous;
        }

        void set(StorageRoute route) {
            this.current = route;
        }
    }
}
