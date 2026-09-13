package com.xjtu.iron.storage.routing.core.resolver;

import com.xjtu.iron.storage.routing.api.CompositeShardKey;
import com.xjtu.iron.storage.routing.api.ShardRouteInfo;
import com.xjtu.iron.storage.routing.api.StorageRoutingException;
import com.xjtu.iron.storage.routing.api.resolver.ShardResolver;

import java.util.Objects;

/**
 * 类型化分片键的哈希解析器，不生成数据源名或表名。
 *
 * <p>单字段：沿用上一版“值文本的 String.hashCode + floorMod”，保留已支持稳定值的原始落点。
 * 因此单字段的字段名和类型不参与哈希，例如字符串 "8" 与整数 8 仍可能位于同一分片。
 * 多字段：对 CompositeShardKey.canonicalForm() 的版本化、带长度前缀编码计算 String.hashCode。</p>
 *
 * <p>字段数量、顺序、类型、编码规则、库表数量均属于路由规则；变更前需要规划数据迁移。
 * 该算法用于确定有限分片，并不承诺哈希无碰撞。</p>
 */
public final class HashShardResolver implements ShardResolver {

    private final int tablesPerDatabase;
    private final int totalShardCount;

    public HashShardResolver(int databaseCount, int tablesPerDatabase) {
        if (databaseCount <= 0 || tablesPerDatabase <= 0) {
            throw new StorageRoutingException("databaseCount and tablesPerDatabase must be positive");
        }
        this.tablesPerDatabase = tablesPerDatabase;
        try {
            this.totalShardCount = Math.multiplyExact(databaseCount, tablesPerDatabase);
        } catch (ArithmeticException ex) {
            throw new StorageRoutingException("totalShardCount exceeds the supported integer range", ex);
        }
    }

    @Override
    public ShardRouteInfo resolve(CompositeShardKey shardKey) {
        Objects.requireNonNull(shardKey, "shardKey must not be null");
        String hashInput = shardKey.size() == 1 ? shardKey.singleKey().value().canonicalText() : shardKey.canonicalForm();
        int shardId = Math.floorMod(hashInput.hashCode(), totalShardCount);
        return new ShardRouteInfo(shardId, shardId / tablesPerDatabase, shardId % tablesPerDatabase, totalShardCount);
    }
}
