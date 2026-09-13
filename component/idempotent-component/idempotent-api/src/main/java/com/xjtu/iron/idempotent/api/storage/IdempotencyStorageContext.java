package com.xjtu.iron.idempotent.api.storage;

/**
 * 一条幂等记录的逻辑存储上下文。
 *
 * <p>它只保存 Idempotency 自己必须稳定持久化的三个维度，不暴露具体数据库、表名、
 * StorageRoute 或 Relational SqlRoute。真正的物理库表位置由 provider/integration 层解析。</p>
 *
 * <ul>
 *     <li>{@code storeName}：逻辑 Store/场景，例如 message-consume、payment；不是 jdbc/redis Provider 名称，
 *         也不是数据库名或表名。</li>
 *     <li>{@code shardKey}：没有业务 StorageRouteContext 时的稳定点路由 fallback key。
 *         如果当前业务链已经绑定 CompositeShardKey/ShardRouteInfo，Storage Routing 集成优先复用业务分片，
 *         不再对该 long 值重复计算分片。</li>
 *     <li>{@code scanBucket}：Reliable Recovery 的逻辑扫描桶。它只用于把“某个物理 shard 内需要扫描的幂等记录”
 *         再切成稳定小桶，绝不等于 databaseIndex、tableIndex 或 shardId。</li>
 * </ul>
 *
 * <p>因此在线访问与恢复扫描分别是：</p>
 * <pre>
 * point access = business bound shardInfo OR fallback shardKey -> physical shard -> idempotency table
 * recovery     = external physical-shard enumeration x scanBucket
 * </pre>
 *
 * <p>该对象是不可变值对象，可以安全地随着 Acquire/Recovery/Write 请求跨层传递；同一幂等物理记录跨 generation
 * 必须保持 storeName/shardKey/scanBucket 稳定。</p>
 */
public final class IdempotencyStorageContext {

    public static final String DEFAULT_STORE_NAME = "default";

    private final String storeName;
    private final long shardKey;
    private final int scanBucket;

    public IdempotencyStorageContext(String storeName, long shardKey, int scanBucket) {
        this.storeName = requireText(storeName, "storeName must not be blank");
        if (scanBucket < 0) {
            throw new IllegalArgumentException("scanBucket must not be negative");
        }
        this.shardKey = shardKey;
        this.scanBucket = scanBucket;
    }

    public static IdempotencyStorageContext of(String storeName, long shardKey, int scanBucket) {
        return new IdempotencyStorageContext(storeName, shardKey, scanBucket);
    }

    public String getStoreName() { return storeName; }
    public long getShardKey() { return shardKey; }
    public int getScanBucket() { return scanBucket; }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
