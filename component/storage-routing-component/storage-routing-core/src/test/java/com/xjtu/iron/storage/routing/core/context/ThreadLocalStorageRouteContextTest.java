package com.xjtu.iron.storage.routing.core.context;

import com.xjtu.iron.storage.routing.api.StorageRoute;
import com.xjtu.iron.storage.routing.api.StorageRouteScope;
import com.xjtu.iron.storage.routing.api.StorageRoutingException;
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
}
