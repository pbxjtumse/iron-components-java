package com.xjtu.iron.storage.routing.core.context;

import com.xjtu.iron.storage.routing.api.context.StorageRouteScope;
import com.xjtu.iron.storage.routing.api.exception.StorageRoutingException;
import com.xjtu.iron.storage.routing.api.route.StorageRoute;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThreadLocalStorageRouteContextTest {

    @Test
    void shouldBindAndClearCurrentRoute() {
        ThreadLocalStorageRouteContext context = new ThreadLocalStorageRouteContext();
        StorageRoute route = StorageRoute.direct("order-db-1", "business_order_001");

        assertThat(context.current()).isEmpty();

        try (StorageRouteScope ignored = context.open(route)) {
            assertThat(context.current()).contains(route);
            assertThat(context.requireCurrent()).isEqualTo(route);
        }

        assertThat(context.current()).isEmpty();
    }

    @Test
    void shouldRestoreOuterRouteAfterInnerScopeClosed() {
        ThreadLocalStorageRouteContext context = new ThreadLocalStorageRouteContext();
        StorageRoute outer = StorageRoute.direct("order-db-1", "business_order_001");
        StorageRoute inner = StorageRoute.direct("order-db-2", "business_order_002");

        try (StorageRouteScope outerScope = context.open(outer)) {
            assertThat(context.requireCurrent()).isEqualTo(outer);

            try (StorageRouteScope innerScope = context.open(inner)) {
                assertThat(context.requireCurrent()).isEqualTo(inner);
            }

            assertThat(context.requireCurrent()).isEqualTo(outer);
        }

        assertThat(context.current()).isEmpty();
    }

    @Test
    void requireCurrentShouldFailWhenNoRouteBound() {
        ThreadLocalStorageRouteContext context = new ThreadLocalStorageRouteContext();

        assertThatThrownBy(context::requireCurrent)
                .isInstanceOf(StorageRoutingException.class)
                .hasMessageContaining("No StorageRoute bound");
    }
    @Test
    void shouldRejectOutOfOrderCloseEvenWhenBothScopesUseSameRoute() {
        var context = new ThreadLocalStorageRouteContext();
        var route = StorageRoute.direct("db_03", "orders_07");
        var outer = context.open(route);
        var inner = context.open(route);

        assertThatThrownBy(outer::close).isInstanceOf(StorageRoutingException.class).hasMessageContaining("close order");
        assertThat(context.current()).isEmpty();
        assertThatThrownBy(inner::close).isInstanceOf(StorageRoutingException.class);
        assertThat(context.current()).isEmpty();
    }

    @Test
    void shouldNotPropagateRouteOrAllowCloseFromAnotherThread() throws InterruptedException {
        var context = new ThreadLocalStorageRouteContext();
        var route = StorageRoute.direct("db_03", "orders_07");
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        try (var scope = context.open(route)) {
            Thread worker = new Thread(() -> {
                try {
                    assertThat(context.current()).isEmpty();
                    assertThatThrownBy(scope::close).isInstanceOf(StorageRoutingException.class).hasMessageContaining("owning thread");
                    assertThat(context.current()).isEmpty();
                } catch (Throwable error) { failure.set(error); }
            });
            worker.start();
            worker.join();
            assertThat(failure.get()).isNull();
            assertThat(context.requireCurrent()).isSameAs(route);
        }
        assertThat(context.current()).isEmpty();
    }

    @Test
    void shouldRestoreOuterRouteOnExceptionAndAllowRepeatedClose() {
        var context = new ThreadLocalStorageRouteContext();
        var outerRoute = StorageRoute.direct("db_03", "orders_07");
        var outer = context.open(outerRoute);
        assertThatThrownBy(() -> {
            try (var inner = context.open(StorageRoute.direct("db_03", "records_07"))) {
                throw new IllegalStateException("business failure");
            }
        }).isInstanceOf(IllegalStateException.class);
        assertThat(context.requireCurrent()).isSameAs(outerRoute);
        outer.close();
        outer.close();
        assertThat(context.current()).isEmpty();
    }
}
