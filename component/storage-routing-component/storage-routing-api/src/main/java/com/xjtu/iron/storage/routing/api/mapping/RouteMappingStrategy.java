package com.xjtu.iron.storage.routing.api.mapping;

import com.xjtu.iron.storage.routing.api.PhysicalStorageLocation;
import com.xjtu.iron.storage.routing.api.ShardRouteInfo;

/**
 * 将逻辑 shard 结果映射为物理存储位置。
 *
 * <p>该接口隔离 shard 计算和物理命名规则。</p>
 */
public interface RouteMappingStrategy {

    /**
     * 根据 shard 信息生成最终物理位置。
     */
    PhysicalStorageLocation map(ShardRouteInfo shardRouteInfo);
}
