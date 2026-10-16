package com.xjtu.iron.idempotent.api.storage;

/**
 * 一条幂等记录的逻辑存储上下文。
 *
 * <p>它只保存 Idempotency 自己必须稳定持久化的两个维度，不暴露具体数据库、表名、
 * StorageRoute 或 Relational SqlRoute。真正的物理库表位置由 provider/integration 层解析。</p>
 *
 * <ul>
 *     <li>{@code storeName}：逻辑 Store/场景，例如 message-consume、payment；不是 jdbc/redis Provider 名称，
 *         也不是数据库名或表名。</li>
 *     <li>{@code scanBucket}：Reliable Recovery 的逻辑扫描桶。它只用于把“某个物理 shard 内需要扫描的幂等记录”
 *         再切成稳定小桶，绝不等于 databaseIndex、tableIndex 或 shardId。</li>
 * </ul>
 *
 * <p>因此在线访问与恢复扫描分别是：</p>
 * <pre>
 * point access = bound business StorageRoute OR idempotency key -> physical shard -> idempotency table
 * recovery     = external physical-shard enumeration x scanBucket
 * </pre>
 *
 * <p>该对象是不可变值对象，可以安全地随着 Acquire/Recovery/Write 请求跨层传递；同一幂等物理记录跨 generation
 * 必须保持 storeName/scanBucket 稳定。物理分片身份由 StorageRouteContext 承载，不在幂等公共模型中重复保存。</p>
 */
public final class IdempotencyStorageContext {

    public static final String DEFAULT_STORE_NAME = "default";

    private final String storeName;
    private final int scanBucket;

    public IdempotencyStorageContext(String storeName, int scanBucket) {
        this.storeName = requireText(storeName, "storeName must not be blank");
        if (scanBucket < 0) {
            throw new IllegalArgumentException("scanBucket must not be negative");
        }
        this.scanBucket = scanBucket;
    }

    public static IdempotencyStorageContext of(String storeName, int scanBucket) {
        return new IdempotencyStorageContext(storeName, scanBucket);
    }

    public String getStoreName() { return storeName; }
    public int getScanBucket() { return scanBucket; }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
