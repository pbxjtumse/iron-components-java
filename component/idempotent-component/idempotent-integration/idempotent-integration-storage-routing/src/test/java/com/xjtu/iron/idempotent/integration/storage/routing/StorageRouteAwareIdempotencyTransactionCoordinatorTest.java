package com.xjtu.iron.idempotent.integration.storage.routing;

import com.xjtu.iron.storage.routing.api.StorageRoute;
import com.xjtu.iron.storage.routing.core.context.ThreadLocalStorageRouteContext;
import com.xjtu.iron.transaction.api.definition.TransactionOptions;
import com.xjtu.iron.transaction.api.definition.TransactionPropagation;
import com.xjtu.iron.transaction.api.execution.TransactionCallback;
import com.xjtu.iron.transaction.api.execution.TransactionExecutor;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class StorageRouteAwareIdempotencyTransactionCoordinatorTest {
    @Test
    void usesPhysicalKeyNotBusinessRouteKeyAndPreservesCheckedExceptions() throws Exception {
        var routes = new ThreadLocalStorageRouteContext();
        var key = new AtomicReference<String>();
        var options = new AtomicReference<TransactionOptions>();
        TransactionExecutor executor = new TransactionExecutor() {
            @Override public <T> T execute(TransactionOptions input, TransactionCallback<T> callback) {
                options.set(input);
                return callback.execute(null);
            }
        };
        var coordinator = new StorageRouteAwareIdempotencyTransactionCoordinator(routes, resourceKey -> { key.set(resourceKey); return executor; });
        try (var scope = routes.open(StorageRoute.direct("db_03", "business_07"))) {
            assertThat(coordinator.executeRequired("order", "merchant-42", () -> "done")).isEqualTo("done");
            assertThat(key).hasValue("db_03");
            assertThat(options.get().propagation()).isEqualTo(TransactionPropagation.REQUIRED);
            Exception failure = new Exception("business failure");
            assertThatThrownBy(() -> coordinator.executeRequired("order", "merchant-42", () -> { throw failure; })).isSameAs(failure);
        }
        assertThat(routes.current()).isEmpty();
        assertThatThrownBy(() -> coordinator.executeRequired("order", null, () -> "never"))
                .isInstanceOf(com.xjtu.iron.storage.routing.api.StorageRoutingException.class);
    }
}
