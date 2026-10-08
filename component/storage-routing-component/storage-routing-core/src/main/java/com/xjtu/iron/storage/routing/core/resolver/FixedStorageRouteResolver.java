package com.xjtu.iron.storage.routing.core.resolver;

import com.xjtu.iron.storage.routing.api.resolver.StorageRouteResolver;
import com.xjtu.iron.storage.routing.api.route.PhysicalStorageLocation;
import com.xjtu.iron.storage.routing.api.route.RouteContext;
import com.xjtu.iron.storage.routing.api.route.storage.StorageRoute;
import java.util.Objects;

/**
 * 固定路由解析器，适用于固定直连、测试与 Demo。
 *
 * <p>只固定物理库表；每次 resolve 都保留调用方传入的 RouteContext，不计算 shardInfo。
 * 若需要按请求计算分片，请使用 DefaultStorageRouteResolver。</p>
 */
public final class FixedStorageRouteResolver implements StorageRouteResolver {

    private final PhysicalStorageLocation physicalLocation;

    public FixedStorageRouteResolver(PhysicalStorageLocation physicalLocation) {
        this.physicalLocation = Objects.requireNonNull(physicalLocation, "physicalLocation must not be null");
    }

    @Override
    public StorageRoute resolve(RouteContext context) {
        return StorageRoute.fixedDirect(Objects.requireNonNull(context, "context must not be null"), physicalLocation);
    }
}
