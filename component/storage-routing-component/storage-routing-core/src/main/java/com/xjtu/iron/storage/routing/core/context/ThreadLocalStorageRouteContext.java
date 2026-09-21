package com.xjtu.iron.storage.routing.core.context;

import com.xjtu.iron.storage.routing.api.context.StorageRouteContext;
import com.xjtu.iron.storage.routing.api.context.StorageRouteScope;
import com.xjtu.iron.storage.routing.api.exception.StorageRoutingException;
import com.xjtu.iron.storage.routing.api.route.storage.StorageRoute;
import java.util.Objects;
import java.util.Optional;

/**
 * 在当前线程传递已经算好的路由，不计算分片，不获取连接，也不开启事务。
 *
 * <p>使用 try (StorageRouteScope scope = context.open(route)) 管理生命周期。每个作用域记住前一个作用域，
 * 关闭时恢复前一个，最外层关闭时 remove。业务预先绑定路由后，幂等装饰器直接复用，不再打开内层作用域。</p>
 *
 * <p>嵌套仅表示上下文的保存和恢复，不表示支持跨库事务；事务中能否使用该数据源由事务集成层检查。
 * 此上下文不跨线程传播，作用域必须在创建它的线程中按后进先出的顺序关闭。</p>
 */
public final class ThreadLocalStorageRouteContext implements StorageRouteContext {

    private final ThreadLocal<RouteScope> currentScope = new ThreadLocal<>();

    @Override
    public Optional<StorageRoute> current() {
        RouteScope scope = currentScope.get();
        return scope == null ? Optional.empty() : Optional.of(scope.route);
    }

    @Override
    public StorageRouteScope open(StorageRoute route) {
        RouteScope scope = new RouteScope(Objects.requireNonNull(route, "route must not be null"), currentScope.get());
        currentScope.set(scope);
        return scope;
    }

    /** 每次 open 都创建独立句柄，即使绑定同一个 route，也能严格检查关闭顺序。 */
    private final class RouteScope implements StorageRouteScope {
        private final StorageRoute route;
        private final RouteScope previous;
        private final Thread owner = Thread.currentThread();
        private boolean closed;

        private RouteScope(StorageRoute route, RouteScope previous) {
            this.route = route;
            this.previous = previous;
        }

        @Override
        public void close() {
            if (Thread.currentThread() != owner) {
                throw new StorageRoutingException("StorageRouteScope must be closed on its owning thread");
            }
            if (closed) return;
            if (currentScope.get() != this) {
                currentScope.remove();
                throw new StorageRoutingException("StorageRouteScope close order is invalid");
            }
            closed = true;
            if (previous == null) currentScope.remove();
            else currentScope.set(previous);
        }
    }
}
