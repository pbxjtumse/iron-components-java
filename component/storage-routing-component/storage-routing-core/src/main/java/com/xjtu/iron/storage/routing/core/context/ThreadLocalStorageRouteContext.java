package com.xjtu.iron.storage.routing.core.context;

import com.xjtu.iron.storage.routing.api.StorageRoute;
import com.xjtu.iron.storage.routing.api.StorageRouteContext;
import com.xjtu.iron.storage.routing.api.StorageRouteScope;
import com.xjtu.iron.storage.routing.api.StorageRoutingException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于 ThreadLocal 的 StorageRouteContext 实现。
 *
 * <p>它支持嵌套作用域：内层 close 后恢复外层 route，最外层 close 后清理 ThreadLocal。
 * 这样可以避免线程池复用时发生 route 泄漏。</p>
 */
public final class ThreadLocalStorageRouteContext implements StorageRouteContext {

    private final ThreadLocal<Deque<StorageRoute>> routes = ThreadLocal.withInitial(ArrayDeque::new);

    @Override
    public Optional<StorageRoute> current() {
        Deque<StorageRoute> stack = routes.get();
        return stack.isEmpty() ? Optional.empty() : Optional.of(stack.peek());
    }

    @Override
    public StorageRouteScope open(StorageRoute route) {
        Objects.requireNonNull(route, "route must not be null");
        Deque<StorageRoute> stack = routes.get();
        stack.push(route);
        return new ThreadLocalStorageRouteScope(this, route);
    }

    private void close(StorageRoute expectedRoute) {
        Deque<StorageRoute> stack = routes.get();
        if (stack.isEmpty()) {
            routes.remove();
            throw new StorageRoutingException("StorageRouteScope close failed because route stack is empty");
        }
        StorageRoute actual = stack.pop();
        if (!Objects.equals(actual, expectedRoute)) {
            stack.clear();
            routes.remove();
            throw new StorageRoutingException("StorageRouteScope close order is invalid");
        }
        if (stack.isEmpty()) {
            routes.remove();
        }
    }

    private static final class ThreadLocalStorageRouteScope implements StorageRouteScope {

        private final ThreadLocalStorageRouteContext context;
        private final StorageRoute route;
        private boolean closed;

        private ThreadLocalStorageRouteScope(ThreadLocalStorageRouteContext context, StorageRoute route) {
            this.context = context;
            this.route = route;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            context.close(route);
        }
    }
}
