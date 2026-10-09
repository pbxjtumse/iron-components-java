package com.xjtu.iron.reliable.task.api.scan;

/** 扫描一个逻辑 Store 中的单个稳定桶。 */
public record ReliableTaskScanRequest(String storeName, int scanBucket, int batchSize) {
    public ReliableTaskScanRequest {
        if (storeName == null || storeName.isBlank()) {
            throw new IllegalArgumentException("storeName must not be blank");
        }
        storeName = storeName.trim();
        if (scanBucket < 0) {
            throw new IllegalArgumentException("scanBucket must not be negative");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be greater than zero");
        }
    }
}
