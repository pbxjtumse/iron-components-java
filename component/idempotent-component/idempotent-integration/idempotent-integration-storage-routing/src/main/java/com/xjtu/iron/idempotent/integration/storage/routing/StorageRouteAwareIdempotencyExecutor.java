package com.xjtu.iron.idempotent.integration.storage.routing;

import com.xjtu.iron.idempotent.api.execution.IdempotencyCallback;
import com.xjtu.iron.idempotent.api.execution.IdempotencyExecutor;
import com.xjtu.iron.idempotent.api.execution.IdempotencyRequest;
import com.xjtu.iron.idempotent.api.execution.IdempotencyResult;
import com.xjtu.iron.idempotent.api.execution.IdempotencyResultStatus;
import com.xjtu.iron.idempotent.api.execution.IdempotencyStage;
import com.xjtu.iron.idempotent.api.recovery.IdempotencyRecoveryRequest;
import com.xjtu.iron.idempotent.api.result.IdempotencyResultPolicy;
import com.xjtu.iron.storage.routing.api.RouteContext;
import com.xjtu.iron.storage.routing.api.StorageRoute;
import com.xjtu.iron.storage.routing.api.StorageRouteContext;
import com.xjtu.iron.storage.routing.api.StorageRouteScope;
import com.xjtu.iron.storage.routing.api.resolver.StorageRouteResolver;

import java.util.Objects;

/**
 * 为一次完整幂等执行绑定唯一 StorageRoute 的执行器装饰器。
 *
 * <p>当业务层已经打开 StorageRouteScope 时直接复用外层路由；否则通过
 * IdempotencyRouteContextFactory + StorageRouteResolver 只计算一次，并让 tryAcquire、业务回调、
 * markSuccess/markFailed 在同一个作用域中执行。离开作用域后自动清理 ThreadLocal。</p>
 */
public final class StorageRouteAwareIdempotencyExecutor implements IdempotencyExecutor {

    private final IdempotencyExecutor delegate;
    private final IdempotencyRouteContextFactory routeContextFactory;
    private final StorageRouteResolver storageRouteResolver;
    private final StorageRouteContext storageRouteContext;

    public StorageRouteAwareIdempotencyExecutor(
            IdempotencyExecutor delegate,
            IdempotencyRouteContextFactory routeContextFactory,
            StorageRouteResolver storageRouteResolver,
            StorageRouteContext storageRouteContext
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.routeContextFactory = Objects.requireNonNull(routeContextFactory, "routeContextFactory must not be null");
        this.storageRouteResolver = Objects.requireNonNull(storageRouteResolver, "storageRouteResolver must not be null");
        this.storageRouteContext = Objects.requireNonNull(storageRouteContext, "storageRouteContext must not be null");
    }

    @Override
    public <T> IdempotencyResult<T> execute(
            IdempotencyRequest request,
            IdempotencyResultPolicy<T> resultPolicy,
            IdempotencyCallback<T> callback
    ) {
        if (request == null || request.getKey() == null || request.getKey().isBlank()) {
            return delegate.execute(request, resultPolicy, callback);
        }
        return withinRoute(
                () -> routeContextFactory.create(request),
                IdempotencyStage.ACQUIRE_STATE,
                () -> delegate.execute(request, resultPolicy, callback));
    }

    @Override
    public <T> IdempotencyResult<T> recover(
            IdempotencyRecoveryRequest request,
            IdempotencyResultPolicy<T> resultPolicy,
            IdempotencyCallback<T> callback
    ) {
        if (request == null || request.getKey() == null || request.getKey().isBlank()) {
            return delegate.recover(request, resultPolicy, callback);
        }
        return withinRoute(
                () -> routeContextFactory.create(request),
                IdempotencyStage.RECOVER_STATE,
                () -> delegate.recover(request, resultPolicy, callback));
    }

    private <T> IdempotencyResult<T> withinRoute(
            RouteInput routeInput,
            IdempotencyStage failureStage,
            RoutedInvocation<T> invocation
    ) {
        // 上层业务已经确定 shard 时，不进行二次 hash，保证业务表、幂等表共享同一个 ShardRouteInfo。
        if (storageRouteContext.current().isPresent()) {
            return invocation.invoke();
        }

        StorageRoute route;
        StorageRouteScope scope;
        try {
            route = Objects.requireNonNull(
                    storageRouteResolver.resolve(Objects.requireNonNull(
                            routeInput.create(), "routeContextFactory returned null")),
                    "storageRouteResolver returned null");
            scope = storageRouteContext.open(route);
        } catch (RuntimeException error) {
            return routeFailure(failureStage, error);
        }

        // 这里只把“创建/解析/绑定路由”的异常归类为 REPOSITORY_ERROR。
        // delegate 的异常语义仍由核心执行器负责，不能在装饰器中误改成路由故障。
        try (scope) {
            return invocation.invoke();
        }
    }

    private <T> IdempotencyResult<T> routeFailure(IdempotencyStage stage, RuntimeException error) {
        return IdempotencyResult.<T>builder()
                .status(IdempotencyResultStatus.REPOSITORY_ERROR)
                .stage(stage)
                .error(error)
                .build();
    }

    @FunctionalInterface
    private interface RoutedInvocation<T> {
        IdempotencyResult<T> invoke();
    }

    @FunctionalInterface
    private interface RouteInput {
        RouteContext create();
    }
}
