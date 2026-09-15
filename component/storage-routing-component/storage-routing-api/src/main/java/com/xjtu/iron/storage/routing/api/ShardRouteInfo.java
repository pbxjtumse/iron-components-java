package com.xjtu.iron.storage.routing.api;

import java.util.Objects;

/**
 * 分片计算结果。
 *
 * <p>ShardRouteInfo 专门描述“算出来的 shard 信息”，与最终物理位置解耦。
 * 例如：orderId -> shardId=56，再由不同 Adapter 决定映射到
 * db_05.order_56 或 db_05.order_06。</p>
 */
public final class ShardRouteInfo {

    /** 全局 shard 编号。 */
    private final int shardId;

    /** 数据库编号。 */
    private final int databaseIndex;

    /** 当前数据库内的表编号。 */
    private final int localTableIndex;

    /** 总 shard 数量。 */
    private final int totalShardCount;

    public ShardRouteInfo(
            int shardId,
            int databaseIndex,
            int localTableIndex,
            int totalShardCount
    ) {
        if (shardId < 0) {
            throw new IllegalArgumentException("shardId must not be negative");
        }
        if (databaseIndex < 0) {
            throw new IllegalArgumentException("databaseIndex must not be negative");
        }
        if (localTableIndex < 0) {
            throw new IllegalArgumentException("localTableIndex must not be negative");
        }
        if (totalShardCount <= 0) {
            throw new IllegalArgumentException("totalShardCount must be positive");
        }
        if (shardId >= totalShardCount || databaseIndex >= totalShardCount || localTableIndex >= totalShardCount) {
            throw new IllegalArgumentException("shard indexes must be less than totalShardCount");
        }
        this.shardId = shardId;
        this.databaseIndex = databaseIndex;
        this.localTableIndex = localTableIndex;
        this.totalShardCount = totalShardCount;
    }

    public int shardId() {
        return shardId;
    }

    public int databaseIndex() {
        return databaseIndex;
    }

    public int localTableIndex() {
        return localTableIndex;
    }

    public int totalShardCount() {
        return totalShardCount;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ShardRouteInfo other)) {
            return false;
        }
        return shardId == other.shardId
                && databaseIndex == other.databaseIndex
                && localTableIndex == other.localTableIndex
                && totalShardCount == other.totalShardCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(shardId, databaseIndex, localTableIndex, totalShardCount);
    }

    @Override
    public String toString() {
        return "ShardRouteInfo{" +
                "shardId=" + shardId +
                ", databaseIndex=" + databaseIndex +
                ", localTableIndex=" + localTableIndex +
                ", totalShardCount=" + totalShardCount +
                '}';
    }
}
