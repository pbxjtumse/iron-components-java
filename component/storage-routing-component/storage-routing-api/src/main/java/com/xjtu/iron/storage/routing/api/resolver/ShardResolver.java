package com.xjtu.iron.storage.routing.api.resolver;

import com.xjtu.iron.storage.routing.api.CompositeShardKey;
import com.xjtu.iron.storage.routing.api.ShardRouteInfo;

/**
 * 分片计算契约：只接收分片字段，不感知场景、逻辑表、扩展属性或物理库表命名。
 * 接口属于 API，具体算法实现属于 Core。
 */
@FunctionalInterface
public interface ShardResolver {

    ShardRouteInfo resolve(CompositeShardKey shardKey);
}
