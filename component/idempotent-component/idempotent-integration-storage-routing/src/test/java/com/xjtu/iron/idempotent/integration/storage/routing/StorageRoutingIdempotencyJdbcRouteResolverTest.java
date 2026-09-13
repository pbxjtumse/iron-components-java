package com.xjtu.iron.idempotent.integration.storage.routing;

import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryQuery;
import com.xjtu.iron.idempotent.api.storage.IdempotencyStorageContext;
import com.xjtu.iron.idempotent.provider.jdbc.routing.IdempotencyJdbcRoute;
import com.xjtu.iron.storage.routing.api.CompositeShardKey;
import com.xjtu.iron.storage.routing.api.PhysicalStorageLocation;
import com.xjtu.iron.storage.routing.api.RouteContext;
import com.xjtu.iron.storage.routing.api.ShardKey;
import com.xjtu.iron.storage.routing.api.StorageRoute;
import com.xjtu.iron.storage.routing.api.StorageRouteContext;
import com.xjtu.iron.storage.routing.api.StorageRouteScope;
import com.xjtu.iron.storage.routing.api.StorageRoutingException;
import com.xjtu.iron.storage.routing.api.resolver.StorageRouteResolver;
import com.xjtu.iron.storage.routing.integration.relational.DefaultStorageRouteToSqlRouteBridge;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageRoutingIdempotencyJdbcRouteResolverTest {

    @Test
    void shouldUseIdempotencyShardKeyWhenNoBusinessRouteIsBound() {
        AtomicReference<RouteContext> captured = new AtomicReference<>();
        StorageRouteResolver routeResolver = context -> {
            captured.set(context);
            return direct(context, "idempotency-db-03", "iron_idempotency_record_017");
        };

        StorageRoutingIdempotencyJdbcRouteResolver resolver = resolver(routeResolver, new TestStorageRouteContext());
        IdempotencyJdbcRoute route = resolver.resolvePoint(
                IdempotencyStorageContext.of("message-consume", 10023L, 417), "message", "MSG-10001");

        assertThat(route.dataSourceKey()).isEqualTo("idempotency-db-03");
        assertThat(route.tableName()).isEqualTo("iron_idempotency_record_017");
        assertThat(captured.get().routeName()).isEqualTo("message-consume");
        assertThat(captured.get().logicalTable()).isEqualTo("iron_idempotency_record");
        assertThat(captured.get().requireShardKey().singleKey().name())
                .isEqualTo(StorageRoutingIdempotencyJdbcRouteResolver.FALLBACK_SHARD_FIELD);
        assertThat(captured.get().attribute("idempotency.scanBucket")).isEqualTo(417);
    }

    @Test
    void shouldReuseBoundBusinessCompositeShardKeyInsteadOfRehashingLongShardKey() {
        CompositeShardKey businessShardKey = CompositeShardKey.of(
                ShardKey.of("tenant_id", 12L),
                ShardKey.of("order_id", 90001L));
        RouteContext businessContext = RouteContext.builder()
                .routeName("order")
                .logicalTable("business_order")
                .shardKey(businessShardKey)
                .build();

        TestStorageRouteContext current = new TestStorageRouteContext();
        current.set(direct(businessContext, "order-db-02", "business_order_031"));

        AtomicReference<RouteContext> captured = new AtomicReference<>();
        StorageRouteResolver routeResolver = context -> {
            captured.set(context);
            return direct(context, "order-db-02", "iron_idempotency_record_031");
        };

        StorageRoutingIdempotencyJdbcRouteResolver resolver = resolver(routeResolver, current);
        resolver.resolvePoint(IdempotencyStorageContext.of("order-write", 999L, 18), "order", "CREATE-90001");

        assertThat(captured.get().shardKey()).isSameAs(businessShardKey);
        assertThat(captured.get().routeName()).isEqualTo("order-write");
        assertThat(captured.get().logicalTable()).isEqualTo("iron_idempotency_record");
    }

    @Test
    void recoveryScanShouldFailFastWhenShardedResolverNeedsKeyButNoShardScopeIsBound() {
        StorageRouteResolver shardRequiredResolver = context -> {
            context.requireShardKey();
            return direct(context, "db-01", "iron_idempotency_record_001");
        };
        StorageRoutingIdempotencyJdbcRouteResolver resolver = resolver(shardRequiredResolver, new TestStorageRouteContext());

        IdempotencyRecoveryQuery query = new IdempotencyRecoveryQuery(
                "message-consume", "message", 7, Instant.parse("2026-09-13T00:00:00Z"), 100);

        assertThatThrownBy(() -> resolver.resolveRecoveryRoutes(query))
                .isInstanceOf(StorageRoutingException.class)
                .hasMessageContaining("external Reliable Task must enumerate a physical shard")
                .hasMessageContaining("scanBucket=7");
    }

    @Test
    void recoveryScanShouldReuseBoundShardAndMapToIdempotencyTable() {
        CompositeShardKey shardKey = CompositeShardKey.of(ShardKey.of("merchant_id", 3001L));
        TestStorageRouteContext current = new TestStorageRouteContext();
        current.set(direct(RouteContext.builder().routeName("payment").logicalTable("payment_order").shardKey(shardKey).build(),
                "payment-db-03", "payment_order_005"));

        AtomicReference<RouteContext> captured = new AtomicReference<>();
        StorageRouteResolver routeResolver = context -> {
            captured.set(context);
            context.requireShardKey();
            return direct(context, "payment-db-03", "iron_idempotency_record_005");
        };

        StorageRoutingIdempotencyJdbcRouteResolver resolver = resolver(routeResolver, current);
        IdempotencyRecoveryQuery query = new IdempotencyRecoveryQuery(
                "payment", "payment", 9, Instant.parse("2026-09-13T00:00:00Z"), 100);

        assertThat(resolver.resolveRecoveryRoutes(query))
                .containsExactly(IdempotencyJdbcRoute.of("payment-db-03", "iron_idempotency_record_005"));
        assertThat(captured.get().shardKey()).isSameAs(shardKey);
    }

    private StorageRoutingIdempotencyJdbcRouteResolver resolver(
            StorageRouteResolver routeResolver,
            StorageRouteContext routeContext
    ) {
        return new StorageRoutingIdempotencyJdbcRouteResolver(
                "iron_idempotency_record", routeResolver, routeContext, new DefaultStorageRouteToSqlRouteBridge());
    }

    private static StorageRoute direct(RouteContext context, String dataSourceKey, String tableName) {
        return StorageRoute.builder()
                .context(context)
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
