package com.xjtu.iron.idempotent.integration.storage.routing;

import com.xjtu.iron.idempotent.api.execution.IdempotencyCallback;
import com.xjtu.iron.idempotent.api.execution.IdempotencyExecutor;
import com.xjtu.iron.idempotent.api.execution.IdempotencyRequest;
import com.xjtu.iron.idempotent.api.execution.IdempotencyResult;
import com.xjtu.iron.idempotent.api.execution.IdempotencyResultStatus;
import com.xjtu.iron.idempotent.api.execution.IdempotencyStage;
import com.xjtu.iron.idempotent.api.recovery.IdempotencyRecoveryRequest;
import com.xjtu.iron.idempotent.api.result.IdempotencyResultPolicy;
import com.xjtu.iron.storage.routing.api.context.StorageRouteContext;
import com.xjtu.iron.storage.routing.api.context.StorageRouteScope;
import com.xjtu.iron.storage.routing.api.resolver.StorageRouteResolver;
import com.xjtu.iron.storage.routing.api.route.RouteContext;
import com.xjtu.iron.storage.routing.api.route.storage.StorageRoute;
import java.util.Objects;

/**
 * 为一次完整幂等执行绑定唯一的 StorageRoute。
 * 【处理执行级路由】外层不重新实现“抢占、重放、成功、失败”的逻辑，做完路由准备就委托给内层。
 * <p>路由来源只有两个：</p>
 *
 * <ol>
 *     <li>
 *         如果业务层已经通过 StorageRouteContext 绑定了路由，
 *         则直接复用当前业务路由，不重新计算。
 *     </li>
 *     <li>
 *         如果当前没有业务路由，则通过 IdempotencyRouteContextFactory
 *         创建默认 RouteContext。默认实现使用幂等 key 作为分片键。
 *     </li>
 * </ol>
 *
 * <p>最终得到的 StorageRoute 会覆盖整个幂等执行过程：</p>
 *
 * <pre>
 * tryAcquire
 *     -> business callback
 *     -> markSuccess / markFailed
 * </pre>
 *
 * <p>因此一次 execute/recover 最多只调用一次全局
 * StorageRouteResolver。</p>
 */
public final class StorageRouteAwareIdempotencyExecutor implements IdempotencyExecutor {

    /**
     * 真正执行幂等状态机的核心执行器。
     *
     * <p>通常是 DefaultIdempotencyExecutor。</p>
     */
    private final IdempotencyExecutor delegate;

    /**
     * 当前没有业务 StorageRoute 时，负责根据幂等请求创建默认 RouteContext。
     *
     * <p>默认实现使用 IdempotencyRequest.key 构造 CompositeShardKey。</p>
     */
    private final IdempotencyRouteContextFactory routeContextFactory;

    /**
     * 将 RouteContext 解析成最终 StorageRoute。
     */
    private final StorageRouteResolver storageRouteResolver;

    /**
     * 保存当前执行作用域绑定的 StorageRoute。
     */
    private final StorageRouteContext storageRouteContext;

    public StorageRouteAwareIdempotencyExecutor(IdempotencyExecutor delegate, IdempotencyRouteContextFactory routeContextFactory,
                                                StorageRouteResolver storageRouteResolver, StorageRouteContext storageRouteContext) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.routeContextFactory = Objects.requireNonNull(routeContextFactory, "routeContextFactory must not be null");
        this.storageRouteResolver = Objects.requireNonNull(storageRouteResolver, "storageRouteResolver must not be null");
        this.storageRouteContext = Objects.requireNonNull(storageRouteContext, "storageRouteContext must not be null");
    }

    @Override
    public <T> IdempotencyResult<T> execute(IdempotencyRequest request, IdempotencyResultPolicy<T> resultPolicy, IdempotencyCallback<T> callback) {
        /*
         * 非法请求仍然交给核心 Executor 统一校验。
         *
         * 路由装饰器不重复定义 IdempotencyRequest 的合法性规则，
         * 避免 Core 和 Integration 出现两套校验结果。
         */
        if (request == null || request.getKey() == null || request.getKey().isBlank()) {
            return delegate.execute(request, resultPolicy, callback);
        }

        return executeWithinRoute(() -> routeContextFactory.create(request), IdempotencyStage.ACQUIRE_STATE,
                () -> delegate.execute(request, resultPolicy, callback));
    }

    @Override
    public <T> IdempotencyResult<T> recover(IdempotencyRecoveryRequest request, IdempotencyResultPolicy<T> resultPolicy, IdempotencyCallback<T> callback) {
        if (request == null || request.getKey() == null || request.getKey().isBlank()) {
            return delegate.recover(request, resultPolicy, callback);
        }

        return executeWithinRoute(() -> routeContextFactory.create(request), IdempotencyStage.RECOVER_STATE,
                () -> delegate.recover(request, resultPolicy, callback));
    }

    private <T> IdempotencyResult<T> executeWithinRoute(RouteContextSupplier defaultRouteContextSupplier,
                                                        IdempotencyStage routeFailureStage, RoutedInvocation<T> invocation) {
        /*
         * 路由分支一：
         *
         * 业务层已经完成路由。
         *
         * 此时不能根据幂等 key 再次计算路由，否则业务表和幂等表
         * 可能落到不同数据库。
         *
         * Repository 后续会读取相同的 StorageRoute.shardInfo，
         * 再映射成幂等表自己的物理表名。
         */
        if (storageRouteContext.current().isPresent()) {
            return invocation.invoke();
        }

        /*
         * 路由分支二：
         *
         * 当前没有业务路由。
         *
         * Factory 默认使用幂等 key 构造 CompositeShardKey，
         * Resolver 根据该 RouteContext 计算一次 StorageRoute。
         */
        StorageRoute resolvedRoute;
        StorageRouteScope routeScope;

        try {
            RouteContext defaultRouteContext = Objects.requireNonNull(defaultRouteContextSupplier.create(), "routeContextFactory returned null");
            resolvedRoute = Objects.requireNonNull(storageRouteResolver.resolve(defaultRouteContext), "storageRouteResolver returned null");
            routeScope = storageRouteContext.open(resolvedRoute);
        } catch (RuntimeException routeError) {
            return routeFailure(routeFailureStage, routeError);
        }

        /*
         * StorageRouteScope 覆盖完整幂等执行。
         *
         * tryAcquire、业务回调和最终状态更新都会读取同一份
         * StorageRoute。执行结束后 Scope 自动恢复或清理
         * ThreadLocal。
         */
        try (routeScope) {
            return invocation.invoke();
        }
    }

    private <T> IdempotencyResult<T> routeFailure(IdempotencyStage stage, RuntimeException error) {
        return IdempotencyResult.<T>builder().status(IdempotencyResultStatus.REPOSITORY_ERROR).stage(stage).error(error).build();
    }

    @FunctionalInterface
    private interface RoutedInvocation<T> {
        IdempotencyResult<T> invoke();
    }

    @FunctionalInterface
    private interface RouteContextSupplier {
        RouteContext create();
    }
}
