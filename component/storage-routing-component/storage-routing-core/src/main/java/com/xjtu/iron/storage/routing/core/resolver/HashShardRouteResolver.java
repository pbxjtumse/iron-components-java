package com.xjtu.iron.storage.routing.core.resolver;

import com.xjtu.iron.storage.routing.api.ShardRouteInfo;
import com.xjtu.iron.storage.routing.api.StorageRouteRequest;
import com.xjtu.iron.storage.routing.api.StorageRoutingException;
import com.xjtu.iron.storage.routing.api.resolver.ShardRouteResolver;

import java.util.Objects;

/**
 * 只计算分片编号的哈希解析器，不生成数据源名或物理表名。
 *
 * <p>保持原 ShardIdHashStorageRouteResolver 的算法：String.valueOf(shardKeyValue).hashCode()，
 * 再用 floorMod 处理负哈希值。已有库表布局不因本次模型拆分而变化。</p>
 *
 * <p>调用方必须保持分片键的字符串表示稳定；修改键表示、算法或分片总数均需要单独的数据迁移方案。</p>
 */
public final class HashShardRouteResolver implements ShardRouteResolver {

    private final int tablesPerDatabase;
    private final int totalShardCount;

    public HashShardRouteResolver(int databaseCount, int tablesPerDatabase) {
        if (databaseCount <= 0 || tablesPerDatabase <= 0) {
            throw new StorageRoutingException("databaseCount and tablesPerDatabase must be positive");
        }
        this.tablesPerDatabase = tablesPerDatabase;
        try {
            // 配置阶段拒绝乘法溢出，避免请求阶段取模失败或悄悄路由到错误分片。
            this.totalShardCount = Math.multiplyExact(databaseCount, tablesPerDatabase);
        } catch (ArithmeticException ex) {
            throw new StorageRoutingException("totalShardCount exceeds the supported integer range", ex);
        }
    }

    @Override
    public ShardRouteInfo resolve(StorageRouteRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        int shardId = Math.floorMod(String.valueOf(request.shardKeyValue()).hashCode(), totalShardCount);
        return new ShardRouteInfo(shardId, shardId / tablesPerDatabase, shardId % tablesPerDatabase, totalShardCount);
    }
}
