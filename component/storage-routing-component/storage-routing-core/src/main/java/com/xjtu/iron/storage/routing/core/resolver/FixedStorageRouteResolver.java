package com.xjtu.iron.storage.routing.core.resolver;

import com.xjtu.iron.storage.routing.api.RouteContext;
import com.xjtu.iron.storage.routing.api.StorageRoute;
import com.xjtu.iron.storage.routing.api.resolver.StorageRouteResolver;

import java.util.Objects;

/**
 * 固定路由解析器，适用于固定直连、测试与 Demo。
 *
 * <p>始终返回构造时提供的同一个 StorageRoute，不使用调用时的 context 覆盖预配置结果。
 * 若需要按请求计算分片并保留输入上下文，请使用 DefaultStorageRouteResolver。</p>
 */
public final class FixedStorageRouteResolver implements StorageRouteResolver {

    private final StorageRoute route;

    public FixedStorageRouteResolver(StorageRoute route) {
        this.route = Objects.requireNonNull(route, "route must not be null");
    }

    @Override
    public StorageRoute resolve(RouteContext context) {
        Objects.requireNonNull(context, "context must not be null");
        return route;
    }
}
