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
 *
 * <p>使用方式：</p>
 *
 * <pre>{@code
 * ThreadLocalStorageRouteContext context = new ThreadLocalStorageRouteContext();
 * StorageRoute route = StorageRoute.direct("order-db-1", "business_order_017");
 *
 * try (StorageRouteScope ignored = context.open(route)) {
 *     // 这里业务 Repository、IdempotencyStorage、OutboxStorage 都能读取同一份 route。
 *     StorageRoute current = context.requireCurrent();
 * }
 * // 离开 try 后，route 会自动清理，避免线程池复用时串到下一次请求。
 * }</pre>
 */
public final class ThreadLocalStorageRouteContext implements StorageRouteContext {

    /**
     * 当前线程持有的路由栈。
     *
     * <p>这里不用单个 ThreadLocal&lt;StorageRoute&gt;，而是使用栈，是为了支持嵌套场景：</p>
     * <pre>{@code
     * open(orderRoute)
     *     open(subTaskRoute)
     *     close(subTaskRoute)  -> 自动恢复 orderRoute
     * close(orderRoute)       -> 清理 ThreadLocal
     * }</pre>
     */
    private final ThreadLocal<Deque<StorageRoute>> routes = ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * 读取当前线程栈顶路由。
     *
     * <p>没有绑定路由时返回 Optional.empty()，调用方可以自己决定是否走默认路由。</p>
     */
    @Override
    public Optional<StorageRoute> current() {
        Deque<StorageRoute> stack = routes.get();
        return stack.isEmpty() ? Optional.empty() : Optional.of(stack.peek());
    }

    /**
     * 打开一个新的路由作用域。
     *
     * <p>执行 open 后，新 route 会压入当前线程路由栈。返回的 StorageRouteScope 必须关闭；
     * 推荐始终使用 try-with-resources，不要手动 set/clear。</p>
     */
    @Override
    public StorageRouteScope open(StorageRoute route) {
        Objects.requireNonNull(route, "route must not be null");
        Deque<StorageRoute> stack = routes.get();
        stack.push(route);
        return new ThreadLocalStorageRouteScope(this, route);
    }

    /**
     * 关闭当前作用域。
     *
     * <p>正常情况下，close 顺序必须和 open 顺序相反。如果顺序错了，说明上下文已经不可信，
     * 这里会直接清空 ThreadLocal 并抛异常，避免错误路由继续向后传播。</p>
     */
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

    /**
     * ThreadLocal 路由作用域。
     *
     * <p>它是一个轻量句柄，只负责在 close 时通知 context 出栈。closed 标记用于保证 close 幂等。</p>
     */
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
