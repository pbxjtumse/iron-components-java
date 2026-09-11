package com.xjtu.iron.storage.routing.api.resolver;

import com.xjtu.iron.storage.routing.api.ShardRouteInfo;
import com.xjtu.iron.storage.routing.api.StorageRouteRequest;

import java.util.Objects;

/**
 * 原接口名的兼容入口；旧请求调用会先转换为类型化分片键。
 * 自定义实现需将实现方法迁移为 resolve(CompositeShardKey)，不能继续依赖请求的其他字段。
 * @deprecated 使用 ShardResolver。
 */
@Deprecated
@FunctionalInterface
public interface ShardRouteResolver extends ShardResolver {

    /**
     * 计算逻辑分片结果。
     */
    default ShardRouteInfo resolve(StorageRouteRequest request) {
        return resolve(Objects.requireNonNull(request, "request must not be null").toContext().requireShardKey());
    }
}
