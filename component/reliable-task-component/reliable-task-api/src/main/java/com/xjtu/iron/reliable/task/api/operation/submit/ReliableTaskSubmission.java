package com.xjtu.iron.reliable.task.api.operation.submit;

import com.xjtu.iron.reliable.task.api.model.ReliableTaskKey;

import java.time.Instant;

/** 创建一条持久化任务的输入；taskId 和 scanBucket 可由 Core 补齐。 */
public final class ReliableTaskSubmission {

    /** 调用方指定的任务 ID；为空时由组件生成。 */
    private final String taskId;
    /** 任务所属的逻辑存储域。 */
    private final String storeName;
    /** 任务所属的业务命名空间。 */
    private final String namespace;
    /** 任务类型，用于选择对应的业务处理器。 */
    private final String taskType;
    /** 业务侧关联键，便于关联原始业务单据。 */
    private final String businessKey;
    /** 提交给任务处理器的序列化业务载荷。 */
    private final String payload;
    /** 存储路由键，用于未来的分库分表定位。 */
    private final String routeKey;
    /** 调用方指定的扫描桶；为空时由组件计算。 */
    private final Integer scanBucket;
    /** 调用方指定的最大尝试次数；为空时采用运行策略默认值。 */
    private final Integer maxAttempts;
    /** 首次允许执行任务的时间；为空时表示立即可执行。 */
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
        /** 调用方指定的任务 ID。 */
        private String taskId;
        /** 任务所属的逻辑存储域。 */
        private String storeName;
        /** 任务所属的业务命名空间。 */
        private String namespace;
        /** 任务类型。 */
        private final String taskType;
        /** 业务侧关联键。 */
        private String businessKey;
        /** 序列化业务载荷。 */
        private final String payload;
        /** 存储路由键。 */
        private String routeKey;
        /** 指定的扫描桶编号。 */
        private Integer scanBucket;
        /** 指定的最大尝试次数。 */
        private Integer maxAttempts;
        /** 指定的首次执行时间。 */
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
