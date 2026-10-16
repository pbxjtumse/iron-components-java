package com.xjtu.iron.retry.api.execution;

import com.xjtu.iron.retry.api.policy.RetryPolicy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 汇总一次逻辑重试执行所需的全部输入。
 *
 * <p>该请求对象避免 RetryExecutor 随着取消、属性和自定义标识增加而不断扩张重载数量。</p>
 *
 * @param <T> 业务返回值类型
 */
public final class RetryExecution<T> {

    /** 稳定操作名称。 */
    private final String operationName;
    /** 调用方业务操作。 */
    private final RetryOperation<T> operation;
    /** 已解析的不可变重试策略。 */
    private final RetryPolicy retryPolicy;
    /** 调用方提供的只读上下文属性。 */
    private final Map<String, Object> attributes;
    /** 协作式取消令牌。 */
    private final RetryCancellationToken cancellationToken;
    /** 调用方指定的逻辑执行标识，为空时由执行器生成。 */
    private final String retryId;

    private RetryExecution(Builder<T> builder) {
        this.operationName = requireText(builder.operationName, "operationName");
        this.operation = Objects.requireNonNull(builder.operation, "operation must not be null");
        this.retryPolicy = Objects.requireNonNull(
                builder.retryPolicy,
                "retryPolicy must not be null"
        );
        this.attributes = immutableAttributes(builder.attributes);
        this.cancellationToken = Objects.requireNonNull(
                builder.cancellationToken,
                "cancellationToken must not be null"
        );
        this.retryId = normalizeOptionalText(builder.retryId, "retryId");
    }

    /** 创建一个执行请求构建器。 */
    public static <T> Builder<T> builder(
            String operationName,
            RetryOperation<T> operation,
            RetryPolicy retryPolicy) {
        return new Builder<>(operationName, operation, retryPolicy);
    }

    public String getOperationName() {
        return operationName;
    }

    public RetryOperation<T> getOperation() {
        return operation;
    }

    public RetryPolicy getRetryPolicy() {
        return retryPolicy;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public RetryCancellationToken getCancellationToken() {
        return cancellationToken;
    }

    public String getRetryId() {
        return retryId;
    }

    /** 构建 RetryExecution 的可变构建器。 */
    public static final class Builder<T> {

        /** 稳定操作名称。 */
        private final String operationName;
        /** 调用方业务操作。 */
        private final RetryOperation<T> operation;
        /** 已解析重试策略。 */
        private final RetryPolicy retryPolicy;
        /** 可选上下文属性。 */
        private Map<String, Object> attributes = Collections.emptyMap();
        /** 默认永不取消。 */
        private RetryCancellationToken cancellationToken = RetryCancellationToken.none();
        /** 可选调用方指定标识。 */
        private String retryId;

        private Builder(
                String operationName,
                RetryOperation<T> operation,
                RetryPolicy retryPolicy) {
            this.operationName = operationName;
            this.operation = operation;
            this.retryPolicy = retryPolicy;
        }

        public Builder<T> attributes(Map<String, Object> attributes) {
            this.attributes = attributes;
            return this;
        }

        public Builder<T> cancellationToken(RetryCancellationToken cancellationToken) {
            this.cancellationToken = cancellationToken;
            return this;
        }

        public Builder<T> retryId(String retryId) {
            this.retryId = retryId;
            return this;
        }

        /** 创建不可变执行请求。 */
        public RetryExecution<T> build() {
            return new RetryExecution<>(this);
        }
    }

    /** 浅复制调用方属性并返回不可修改的映射视图。 */
    private static Map<String, Object> immutableAttributes(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }

    /** 校验必填文本非空且非空白。 */
    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    /** 规范化可选文本并拒绝只有空白的显式输入。 */
    private static String normalizeOptionalText(String value, String name) {
        if (value == null) {
            return null;
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank when provided");
        }
        return value.trim();
    }
}
