package com.xjtu.iron.storage.routing.core.resolver;

import com.xjtu.iron.storage.routing.api.StorageRoute;
import com.xjtu.iron.storage.routing.api.StorageRouteRequest;
import com.xjtu.iron.storage.routing.api.StorageRouteResolver;

import java.util.Objects;

/**
 * 固定路由解析器。
 *
 * <p>主要用于测试、Demo、单库或临时接入场景。无论输入请求是什么，都会返回同一个 StorageRoute。</p>
 */
public final class FixedStorageRouteResolver implements StorageRouteResolver {

    private final StorageRoute route;

    public FixedStorageRouteResolver(StorageRoute route) {
        this.route = Objects.requireNonNull(route, "route must not be null");
    }

    @Override
    public StorageRoute resolve(StorageRouteRequest request) {
        return route;
    }
}
