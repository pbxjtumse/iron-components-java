package com.xjtu.iron.storage.routing.api.resolver;

import com.xjtu.iron.storage.routing.api.StorageRoute;
import com.xjtu.iron.storage.routing.api.RouteContext;

/**
 * Storage Route 总入口。
 *
 * <p>业务侧只依赖该接口，不感知 shard 计算、物理库表映射以及底层实现。</p>
 */
@FunctionalInterface
public interface StorageRouteResolver {

    /**
     * 根据输入上下文生成完整 StorageRoute。
     */
    StorageRoute resolve(RouteContext context);
}
