package com.xjtu.iron.idempotent.integration.storage.routing;

import com.xjtu.iron.idempotent.api.execution.IdempotencyCallback;
import com.xjtu.iron.idempotent.api.execution.IdempotencyExecutor;
import com.xjtu.iron.idempotent.api.execution.IdempotencyRequest;
import com.xjtu.iron.idempotent.api.execution.IdempotencyResult;
import com.xjtu.iron.idempotent.api.execution.IdempotencyResultStatus;
import com.xjtu.iron.idempotent.api.execution.IdempotencyStage;
import com.xjtu.iron.idempotent.api.recovery.IdempotencyRecoveryRequest;
import com.xjtu.iron.idempotent.api.result.IdempotencyResultPolicy;
import com.xjtu.iron.storage.routing.api.PhysicalStorageLocation;
import com.xjtu.iron.storage.routing.api.RouteContext;
import com.xjtu.iron.storage.routing.api.StorageRoute;
import com.xjtu.iron.storage.routing.core.context.ThreadLocalStorageRouteContext;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class StorageRouteAwareIdempotencyExecutorTest {

    @Test
    void shouldResolveOnceAndExposeSameRouteForTheWholeExecution() {
        ThreadLocalStorageRouteContext routeContext = new ThreadLocalStorageRouteContext();
        AtomicInteger resolutions = new AtomicInteger();
        AtomicReference<StorageRoute> firstRead = new AtomicReference<>();

        IdempotencyExecutor delegate = new StubExecutor(() -> {
            firstRead.set(routeContext.requireCurrent());
            assertThat(routeContext.requireCurrent()).isSameAs(firstRead.get());
            assertThat(routeContext.requireCurrent()).isSameAs(firstRead.get());
        });

        StorageRouteAwareIdempotencyExecutor executor = new StorageRouteAwareIdempotencyExecutor(
                delegate,
                new DefaultIdempotencyRouteContextFactory("iron_idempotency_record"),
                input -> {
                    resolutions.incrementAndGet();
                    return StorageRoute.builder()
                            .context(input)
                            .location(PhysicalStorageLocation.of("db_03", "iron_idempotency_record_07"))
                            .build();
                },
                routeContext);

        IdempotencyResult<String> result = executor.execute(
                IdempotencyRequest.builder().key("PAY-10001").storeName("payment").build(),
                context -> "ok");

        assertThat(result.getStatus()).isEqualTo(IdempotencyResultStatus.EXECUTED);
        assertThat(resolutions).hasValue(1);
        assertThat(firstRead.get().context().requireShardKey().singleKey().name())
                .isEqualTo(DefaultIdempotencyRouteContextFactory.IDEMPOTENCY_KEY_FIELD);
        assertThat(firstRead.get().context().requireShardKey().singleKey().value().canonicalText())
                .isEqualTo("PAY-10001");
        assertThat(routeContext.current()).isEmpty();
    }

    @Test
    void shouldReuseBusinessRouteWithoutResolvingAgain() {
        ThreadLocalStorageRouteContext routeContext = new ThreadLocalStorageRouteContext();
        StorageRoute businessRoute = StorageRoute.builder()
                .context(RouteContext.builder().routeName("order").logicalTable("business_order").build())
                .location(PhysicalStorageLocation.of("db_08", "business_order_03"))
                .build();
        AtomicInteger resolutions = new AtomicInteger();

        IdempotencyExecutor delegate = new StubExecutor(
                () -> assertThat(routeContext.requireCurrent()).isSameAs(businessRoute));
        StorageRouteAwareIdempotencyExecutor executor = new StorageRouteAwareIdempotencyExecutor(
                delegate,
                new DefaultIdempotencyRouteContextFactory("iron_idempotency_record"),
                input -> {
                    resolutions.incrementAndGet();
                    throw new AssertionError("bound business route must be reused");
                },
                routeContext);

        try (var ignored = routeContext.open(businessRoute)) {
            IdempotencyResult<String> result = executor.execute(IdempotencyRequest.of("ORDER-1"), context -> "ok");
            assertThat(result.getStatus()).isEqualTo(IdempotencyResultStatus.EXECUTED);
            assertThat(routeContext.requireCurrent()).isSameAs(businessRoute);
        }

        assertThat(resolutions).hasValue(0);
        assertThat(routeContext.current()).isEmpty();
    }

    private static final class StubExecutor implements IdempotencyExecutor {
        private final Runnable assertion;

        private StubExecutor(Runnable assertion) {
            this.assertion = assertion;
        }

        @Override
        public <T> IdempotencyResult<T> execute(
                IdempotencyRequest request,
                IdempotencyResultPolicy<T> resultPolicy,
                IdempotencyCallback<T> callback
        ) {
            assertion.run();
            return IdempotencyResult.<T>builder()
                    .status(IdempotencyResultStatus.EXECUTED)
                    .stage(IdempotencyStage.COMPLETE_STATE)
                    .build();
        }

        @Override
        public <T> IdempotencyResult<T> recover(
                IdempotencyRecoveryRequest request,
                IdempotencyResultPolicy<T> resultPolicy,
                IdempotencyCallback<T> callback
        ) {
            assertion.run();
            return IdempotencyResult.<T>builder()
                    .status(IdempotencyResultStatus.RECOVERED)
                    .stage(IdempotencyStage.COMPLETE_STATE)
                    .build();
        }
    }
}
