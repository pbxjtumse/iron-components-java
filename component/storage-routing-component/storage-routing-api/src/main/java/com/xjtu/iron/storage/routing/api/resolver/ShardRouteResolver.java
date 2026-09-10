package com.xjtu.iron.storage.routing.api.resolver;

import com.xjtu.iron.storage.routing.api.ShardRouteInfo;
import com.xjtu.iron.storage.routing.api.StorageRouteRequest;

/**
 * 根据业务请求计算逻辑 shard。
 *
 * <p>该接口只负责：业务请求 -> ShardRouteInfo。</p>
 *
 * <p>它不知道数据库名字，也不知道物理表名字。</p>
 */
@FunctionalInterface
public interface ShardRouteResolver {

    /**
     * 计算逻辑分片结果。
     */
    ShardRouteInfo resolve(StorageRouteRequest request);
}
