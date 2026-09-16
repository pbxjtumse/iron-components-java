package com.xjtu.iron.idempotent.api.execution;

import com.xjtu.iron.idempotent.api.policy.IdempotencyPolicy;
import com.xjtu.iron.idempotent.api.storage.IdempotencyStorageContext;

/**
 * 普通幂等执行请求，只描述“这一次逻辑请求是谁以及应该落到哪个逻辑存储位置”。
 *
 * <ul>
 *     <li>{@code key}：逻辑请求身份证；同一次 HTTP 重试/MQ 重投必须保持相同；</li>
 *     <li>{@code requestHash}：业务内容指纹，防止同 key 被不同参数错误复用；</li>
 *     <li>{@code routeKey}：业务路由元数据，例如租户/商户路由；它不再承担存储分片职责；</li>
 *     <li>{@code storeName / shardKey / scanBucket}：V2 Shard-Ready Storage 元数据；</li>
 *     <li>{@code policyName / policy}：选择“这一类业务应该怎么做幂等”。</li>
 * </ul>
 *
 * <p>策略解析优先级：inline policy &gt; policyName &gt; default policy。</p>
 */
public final class IdempotencyRequest {

    /** 逻辑幂等 Key；同一次用户动作、消息或业务命令的重试必须保持一致。 */
    private final String key;

    /** 请求业务内容指纹；用于识别同 key 携带不同参数的错误复用。 */
    private final String requestHash;

    /** 业务路由元数据，例如租户、商户或订单路由；不等于幂等存储分片键。不直接参与默认物理分片 */
    private final String routeKey;

    /** 逻辑 Store 名称，用来隔离不同物理/逻辑存储域，默认 default。 */
    private final String storeName;

    /** 在线点查/写入的稳定分片路由键，为后续分库分表预留。 */
    private final long shardKey;

    /** Reliable Recovery 的逻辑扫描桶，必须和首次请求保持一致。 */
    private final int scanBucket;

    /** 命名 Policy；为空时由 PolicyRegistry 选择默认策略。 */
    private final String policyName;

    /** 调用级内联 Policy；存在时优先于 policyName。 */
    private final IdempotencyPolicy policy;

    private IdempotencyRequest(Builder builder) {
        this.key = builder.key;
        this.requestHash = builder.requestHash;
        this.routeKey = builder.routeKey;
        this.storeName = normalizeStoreName(builder.storeName);
        this.shardKey = builder.shardKey;
        this.scanBucket = builder.scanBucket;
        this.policyName = builder.policyName;
        this.policy = builder.policy;
    }

    public static Builder builder() { return new Builder(); }
    public static IdempotencyRequest of(String key) { return builder().key(key).build(); }
    public static IdempotencyRequest of(String key, String policyName) { return builder().key(key).policyName(policyName).build(); }

    public String getKey() { return key; }
    public String getRequestHash() { return requestHash; }
    public String getRouteKey() { return routeKey; }
    public String getStoreName() { return storeName; }
    public long getShardKey() { return shardKey; }
    public int getScanBucket() { return scanBucket; }
    public String getPolicyName() { return policyName; }
    public IdempotencyPolicy getPolicy() { return policy; }

    public IdempotencyStorageContext storageContext() {
        return IdempotencyStorageContext.of(storeName, shardKey, scanBucket);
    }

    private static String normalizeStoreName(String value) {
        return value == null || value.isBlank() ? IdempotencyStorageContext.DEFAULT_STORE_NAME : value.trim();
    }

    public static final class Builder {
        /** 逻辑幂等 Key，必填，由 Executor 统一校验非空。 */
        private String key;

        /** 可选请求指纹；建议用稳定序列化后的 SHA-256。 */
        private String requestHash;

        /** 可选业务路由信息；Recovery 任务应原样沿用首次 routeKey。 */
        private String routeKey;

        /** 逻辑 Store，默认 default；不要填写 jdbc/redis 这种 Provider 名称。 */
        private String storeName = IdempotencyStorageContext.DEFAULT_STORE_NAME;

        /** 分片键，默认 0；业务接入分片后应使用稳定值。 */
        private long shardKey;

        /** 扫描桶，默认 0；不能为负数。 */
        private int scanBucket;

        /** 命名策略。 */
        private String policyName;

        /** 内联策略，适合测试或少量调用级覆盖。 */
        private IdempotencyPolicy policy;

        public Builder key(String value) { this.key = value; return this; }
        public Builder requestHash(String value) { this.requestHash = value; return this; }
        public Builder routeKey(String value) { this.routeKey = value; return this; }
        public Builder storeName(String value) { this.storeName = value; return this; }
        public Builder shardKey(long value) { this.shardKey = value; return this; }
        public Builder scanBucket(int value) { this.scanBucket = value; return this; }
        public Builder policyName(String value) { this.policyName = value; return this; }
        public Builder policy(IdempotencyPolicy value) { this.policy = value; return this; }

        public IdempotencyRequest build() {
            if (scanBucket < 0) {
                throw new IllegalArgumentException("scanBucket must not be negative");
            }
            return new IdempotencyRequest(this);
        }
    }
}
