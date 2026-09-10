package com.xjtu.iron.storage.routing.core.resolver;

import com.xjtu.iron.storage.routing.api.StorageRoute;
import com.xjtu.iron.storage.routing.api.StorageRouteRequest;
import com.xjtu.iron.storage.routing.api.StorageRouteResolver;

import java.util.Objects;

/**
 * 固定路由解析器。
 *
 * <p>主要用于测试、Demo、单库或临时接入场景。无论输入请求是什么，都会返回同一个 StorageRoute。</p>
 *
 * <p>示例：</p>
 *
 * <pre>{@code
 * StorageRoute route = StorageRoute.direct("order-db-0", "business_order");
 * StorageRouteResolver resolver = new FixedStorageRouteResolver(route);
 *
 * StorageRoute actual = resolver.resolve(StorageRouteRequest.of("business_order", "order_id", "order-10001"));
 * // actual 永远等于 route
 * }</pre>
 *
 * <p>注意：它不读取 request.logicalTable()、request.shardKeyName()、request.shardKeyValue()。
 * 因此它不适合真正分库分表生产路由，只适合单库、测试、Demo、临时兜底。</p>
 */
public final class FixedStorageRouteResolver implements StorageRouteResolver {

    /**
     * 固定返回的路由结果。
     */
    private final StorageRoute route;

    public FixedStorageRouteResolver(StorageRoute route) {
        this.route = Objects.requireNonNull(route, "route must not be null");
    }

    /**
     * 忽略 request，直接返回构造函数中传入的 route。
     */
    @Override
    public StorageRoute resolve(StorageRouteRequest request) {
        return route;
    }
}
