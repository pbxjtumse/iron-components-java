package com.xjtu.iron.storage.routing.api.mapping;

import com.xjtu.iron.storage.routing.api.route.PhysicalStorageLocation;
import com.xjtu.iron.storage.routing.api.route.ShardRouteInfo;

/**
 * 将逻辑 shard 结果映射为物理存储位置。
 *
 * <p>该接口隔离 shard 计算和物理命名规则。</p>
 */
@FunctionalInterface
public interface RouteMappingStrategy {

    /**
     * 根据 shard 信息生成最终物理位置。
     * 假如全局编号 tablePrefix + shardRouteInfo.shardId()  db_05.order_56
     * db_00.order_00 ~ order_09
     * db_01.order_10 ~ order_19
     * ...
     * db_05.order_50 ~ order_59
     * 【分片信息】
     *  shardId=56
     *  databaseIndex=5
     *  localTableIndex=6
     * 【物理信息】
     *  dataSourceKey + tableName
     */
    PhysicalStorageLocation map(ShardRouteInfo shardRouteInfo);
}
