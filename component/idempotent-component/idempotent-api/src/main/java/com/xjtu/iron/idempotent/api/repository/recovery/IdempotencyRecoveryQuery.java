package com.xjtu.iron.idempotent.api.repository.recovery;

import java.time.Instant;

/**
 * 外部任务组件查询恢复候选项时使用的分桶扫描条件。
 *
 * <p>V2 不再用 routeKey 扫描大表。在线物理路由交给外层 Storage Routing；Reliable Recovery 使用稳定的 scanBucket 分桶扫描。</p>
 * <p>它只是 Repository 查询协议，不代表幂等组件自己拥有定时扫描线程。</p>
 */
public final class IdempotencyRecoveryQuery {

    /** 要扫描的逻辑 Store。 */
    private final String storeName;

    /** Policy 解析出的业务隔离域。 */
    private final String namespace;

    /** 本次扫描负责的逻辑桶。 */
    private final int scanBucket;

    /** 扫描判断使用的当前时间。 */
    private final Instant now;

    /** 最大返回 candidate 数量。 */
    private final int limit;

    public IdempotencyRecoveryQuery(String storeName, String namespace, int scanBucket, Instant now, int limit) {
        this.storeName = storeName;
        this.namespace = namespace;
        this.scanBucket = scanBucket;
        this.now = now;
        this.limit = limit;
    }

    public String getStoreName() { return storeName; }
    public String getNamespace() { return namespace; }
    public int getScanBucket() { return scanBucket; }
    public Instant getNow() { return now; }
    public int getLimit() { return limit; }
}
