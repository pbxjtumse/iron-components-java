package com.xjtu.iron.reliable.task.api.repository.scan;

import java.time.Instant;
import java.util.Objects;

/** 一个逻辑 Store 内单个扫描桶的到期任务查询。 */
public final class ReliableTaskScanQuery {

    /** 需要扫描的逻辑任务 Store。 */
    private final String storeName;

    /** 需要扫描的稳定桶编号。 */
    private final int scanBucket;

    /** 判断任务和 Lease 是否到期的时间基准。 */
    private final Instant now;

    /** Repository 本次最多返回的候选数量。 */
    private final int limit;

    public ReliableTaskScanQuery(String storeName, int scanBucket, Instant now, int limit) {
        if (storeName == null || storeName.isBlank()) {
            throw new IllegalArgumentException("storeName must not be blank");
        }
        if (scanBucket < 0) {
            throw new IllegalArgumentException("scanBucket must not be negative");
        }
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be greater than zero");
        }
        this.storeName = storeName.trim();
        this.scanBucket = scanBucket;
        this.now = now;
        this.limit = limit;
    }

    public String getStoreName() {
        return storeName;
    }

    public int getScanBucket() {
        return scanBucket;
    }

    public Instant getNow() {
        return now;
    }

    public int getLimit() {
        return limit;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ReliableTaskScanQuery that)) {
            return false;
        }
        return scanBucket == that.scanBucket
                && limit == that.limit
                && storeName.equals(that.storeName)
                && now.equals(that.now);
    }

    @Override
    public int hashCode() {
        return Objects.hash(storeName, scanBucket, now, limit);
    }

    @Override
    public String toString() {
        return "ReliableTaskScanQuery{" +
                "storeName='" + storeName + '\'' +
                ", scanBucket=" + scanBucket +
                ", now=" + now +
                ", limit=" + limit +
                '}';
    }
}
