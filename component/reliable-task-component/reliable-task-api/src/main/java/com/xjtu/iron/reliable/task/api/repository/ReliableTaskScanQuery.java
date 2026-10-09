package com.xjtu.iron.reliable.task.api.repository;

import java.time.Instant;

/** 一个逻辑 Store 内单个扫描桶的到期任务查询。 */
public record ReliableTaskScanQuery(String storeName, int scanBucket, Instant now, int limit) {
    public ReliableTaskScanQuery {
        if (storeName == null || storeName.isBlank()) {
            throw new IllegalArgumentException("storeName must not be blank");
        }
        storeName = storeName.trim();
        if (scanBucket < 0) {
            throw new IllegalArgumentException("scanBucket must not be negative");
        }
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be greater than zero");
        }
    }
}
