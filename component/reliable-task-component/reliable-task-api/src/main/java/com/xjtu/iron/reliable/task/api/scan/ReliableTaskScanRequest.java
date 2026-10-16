package com.xjtu.iron.reliable.task.api.scan;

import java.util.Objects;

/** 扫描一个逻辑 Store 中的单个稳定桶。 */
public final class ReliableTaskScanRequest {

    /** 本次扫描目标所属的逻辑任务 Store。 */
    private final String storeName;

    /** 本次扫描的稳定桶编号。 */
    private final int scanBucket;

    /** 本次扫描最多读取的候选任务数量。 */
    private final int batchSize;

    public ReliableTaskScanRequest(String storeName, int scanBucket, int batchSize) {
        if (storeName == null || storeName.isBlank()) {
            throw new IllegalArgumentException("storeName must not be blank");
        }
        if (scanBucket < 0) {
            throw new IllegalArgumentException("scanBucket must not be negative");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be greater than zero");
        }
        this.storeName = storeName.trim();
        this.scanBucket = scanBucket;
        this.batchSize = batchSize;
    }

    public String getStoreName() {
        return storeName;
    }

    public int getScanBucket() {
        return scanBucket;
    }

    public int getBatchSize() {
        return batchSize;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ReliableTaskScanRequest that)) {
            return false;
        }
        return scanBucket == that.scanBucket
                && batchSize == that.batchSize
                && storeName.equals(that.storeName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(storeName, scanBucket, batchSize);
    }

    @Override
    public String toString() {
        return "ReliableTaskScanRequest{" +
                "storeName='" + storeName + '\'' +
                ", scanBucket=" + scanBucket +
                ", batchSize=" + batchSize +
                '}';
    }
}
