package com.xjtu.iron.reliable.task.api.model;

import java.time.Instant;

/** 创建一条持久化任务的输入；taskId 和 scanBucket 可由 Core 补齐。 */
public final class ReliableTaskSubmission {

    private final String taskId;
    private final String storeName;
    private final String namespace;
    private final String taskType;
    private final String businessKey;
    private final String payload;
    private final String routeKey;
    private final Integer scanBucket;
    private final Integer maxAttempts;
    private final Instant firstExecuteAt;

    private ReliableTaskSubmission(Builder builder) {
        this.taskId = normalize(builder.taskId);
        this.storeName = defaultText(builder.storeName, ReliableTaskKey.DEFAULT_STORE_NAME);
        this.namespace = defaultText(builder.namespace, ReliableTaskKey.DEFAULT_NAMESPACE);
        this.taskType = requireText(builder.taskType, "taskType");
        this.businessKey = normalize(builder.businessKey);
        this.payload = builder.payload == null ? "" : builder.payload;
        this.routeKey = normalize(builder.routeKey);
        if (builder.scanBucket != null && builder.scanBucket < 0) {
            throw new IllegalArgumentException("scanBucket must not be negative");
        }
        this.scanBucket = builder.scanBucket;
        if (builder.maxAttempts != null && builder.maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be greater than zero");
        }
        this.maxAttempts = builder.maxAttempts;
        this.firstExecuteAt = builder.firstExecuteAt;
    }

    public static Builder builder(String taskType, String payload) {
        return new Builder(taskType, payload);
    }

    public String getTaskId() { return taskId; }
    public String getStoreName() { return storeName; }
    public String getNamespace() { return namespace; }
    public String getTaskType() { return taskType; }
    public String getBusinessKey() { return businessKey; }
    public String getPayload() { return payload; }
    public String getRouteKey() { return routeKey; }
    public Integer getScanBucket() { return scanBucket; }
    public Integer getMaxAttempts() { return maxAttempts; }
    public Instant getFirstExecuteAt() { return firstExecuteAt; }

    public static final class Builder {
        private String taskId;
        private String storeName;
        private String namespace;
        private final String taskType;
        private String businessKey;
        private final String payload;
        private String routeKey;
        private Integer scanBucket;
        private Integer maxAttempts;
        private Instant firstExecuteAt;

        private Builder(String taskType, String payload) {
            this.taskType = taskType;
            this.payload = payload;
        }

        public Builder taskId(String taskId) { this.taskId = taskId; return this; }
        public Builder storeName(String storeName) { this.storeName = storeName; return this; }
        public Builder namespace(String namespace) { this.namespace = namespace; return this; }
        public Builder businessKey(String businessKey) { this.businessKey = businessKey; return this; }
        public Builder routeKey(String routeKey) { this.routeKey = routeKey; return this; }
        public Builder scanBucket(int scanBucket) { this.scanBucket = scanBucket; return this; }
        public Builder maxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; return this; }
        public Builder firstExecuteAt(Instant firstExecuteAt) { this.firstExecuteAt = firstExecuteAt; return this; }
        public ReliableTaskSubmission build() { return new ReliableTaskSubmission(this); }
    }

    private static String defaultText(String value, String defaultValue) {
        String normalized = normalize(value);
        return normalized == null ? defaultValue : normalized;
    }

    private static String requireText(String value, String name) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
