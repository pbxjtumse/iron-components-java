package com.xjtu.iron.message.api.model;
import java.util.Objects;

/**
 * 表示需要跨消息边界传播的稳定业务上下文。描述的是 【当前这次处理环境是什么】
 *
 * <p>Trace、Span、MDC 等技术上下文不在这里固化字段，而是通过消息头传播，
 * 这样消息 API 不会绑定 OpenTelemetry、Brave、SkyWalking 等具体实现。</p>
 *
 * <p>{@code source}：生产消息的应用或服务标识；允许为空，由 core 根据组件配置补齐</p>
 * <p>{@code correlationId}：同一业务过程、Saga 或调用链中的关联标识</p>
 * <p>{@code causationId}：直接触发当前消息的上游消息 ID；首条消息通常为空</p>
 * <p>{@code tenantId}：多租户场景中的租户标识；非多租户系统允许为空</p>
 */
public final class MessageContext {
    /** 生产消息的应用或服务标识；允许为空，由 core 根据组件配置补齐。 */
    private final String source;

    /** 同一业务过程、Saga 或调用链中的关联标识。 */
    private final String correlationId;

    /** 直接触发当前消息的上游消息 ID；首条消息通常为空。 */
    private final String causationId;

    /** 多租户场景中的租户标识；非多租户系统允许为空。 */
    private final String tenantId;

    /**
     * 统一标准化可选上下文字段。
     */
    public MessageContext(
        String source,
        String correlationId,
        String causationId,
        String tenantId) {
        // source 允许为空，但不保留无意义首尾空白。
        source = normalize(source);
        // correlationId 允许发送前为空，由 core 在发送前补齐。
        correlationId = normalize(correlationId);
        // causationId 对根消息通常为空。
        causationId = normalize(causationId);
        // 非多租户系统 tenantId 可以为空。
        tenantId = normalize(tenantId);
    
        // 保存完成校验和标准化后的 source。
        this.source = source;
        // 保存完成校验和标准化后的 correlationId。
        this.correlationId = correlationId;
        // 保存完成校验和标准化后的 causationId。
        this.causationId = causationId;
        // 保存完成校验和标准化后的 tenantId。
        this.tenantId = tenantId;
    }

    /** 标准化可选文本。 */
    private static String normalize(String value) {
        // null 表示没有提供该上下文字段。
        if (value == null) {
            // 直接保持 null。
            return null;
        }
        // 去除调用方误带的首尾空白。
        String trimmed = value.trim();
        // 空白文本统一转换为 null。
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 返回一个所有字段都为空的上下文对象。
     *
     * @return 空上下文
     */
    public static MessageContext empty() {
        // 使用显式对象代替 null，降低业务调用和 core 丰富过程中的空判断数量。
        return new MessageContext(null, null, null, null);
    }

    /**
     * 创建上下文 Builder。
     *
     * @return 新 Builder
     */
    public static Builder builder() {
        // Builder 避免四个同类型字符串参数发生顺序误用。
        return new Builder();
    }

    /**
     * 返回复制当前值的 Builder。
     *
     * @return 已复制当前字段的 Builder
     */
    public Builder toBuilder() {
        // 将当前不可变值复制到可变 Builder，便于只调整单个字段。
        return new Builder()
                .source(source)
                .correlationId(correlationId)
                .causationId(causationId)
                .tenantId(tenantId);
    }

    /**
     * 用于构造不可变 MessageContext 的 Builder。
     */
    public static final class Builder {

        /** 生产消息的应用或服务标识。 */
        private String source;

        /** 同一业务过程中的关联标识。 */
        private String correlationId;

        /** 直接上游消息 ID。 */
        private String causationId;

        /** 租户标识。 */
        private String tenantId;

        /**
         * 设置消息来源。
         *
         * @param source 消息来源
         * @return 当前 Builder
         */
        public Builder source(String source) {
            // 保存调用方显式传入的来源；空值会在 core 中按默认配置处理。
            this.source = source;
            // 返回当前 Builder 以支持链式调用。
            return this;
        }

        /**
         * 设置关联标识。
         *
         * @param correlationId 关联标识
         * @return 当前 Builder
         */
        public Builder correlationId(String correlationId) {
            // 关联标识可以是订单号、流程实例号或首条消息 ID。
            this.correlationId = correlationId;
            // 返回当前 Builder 以支持链式调用。
            return this;
        }

        /**
         * 设置直接上游消息 ID。
         *
         * @param causationId 上游消息 ID
         * @return 当前 Builder
         */
        public Builder causationId(String causationId) {
            // causationId 只描述直接父子关系，不替代 correlationId。
            this.causationId = causationId;
            // 返回当前 Builder 以支持链式调用。
            return this;
        }

        /**
         * 设置租户标识。
         *
         * @param tenantId 租户标识
         * @return 当前 Builder
         */
        public Builder tenantId(String tenantId) {
            // 保存租户信息，非多租户场景允许保持为空。
            this.tenantId = tenantId;
            // 返回当前 Builder 以支持链式调用。
            return this;
        }

        /**
         * 构造不可变上下文。
         *
         * @return 不可变上下文
         */
        public MessageContext build() {
            // 普通类通过 final 字段保持不可变，因此可直接安全返回。
            return new MessageContext(source, correlationId, causationId, tenantId);
        }
    }
    /**
     * 返回生产消息的应用或服务标识；允许为空，由 core 根据组件配置补齐。
     *
     * @return 生产消息的应用或服务标识；允许为空，由 core 根据组件配置补齐
     */
    public String source() {
        // 返回不可变字段。
        return source;
    }

    /**
     * 返回同一业务过程、Saga 或调用链中的关联标识。
     *
     * @return 同一业务过程、Saga 或调用链中的关联标识
     */
    public String correlationId() {
        // 返回不可变字段。
        return correlationId;
    }

    /**
     * 返回直接触发当前消息的上游消息 ID；首条消息通常为空。
     *
     * @return 直接触发当前消息的上游消息 ID；首条消息通常为空
     */
    public String causationId() {
        // 返回不可变字段。
        return causationId;
    }

    /**
     * 返回多租户场景中的租户标识；非多租户系统允许为空。
     *
     * @return 多租户场景中的租户标识；非多租户系统允许为空
     */
    public String tenantId() {
        // 返回不可变字段。
        return tenantId;
    }

    /**
     * 按全部字段比较两个值对象。
     *
     * @param object 待比较对象
     * @return 字段值全部一致时返回 true
     */
    @Override
    public boolean equals(Object object) {
        // 同一对象直接相等。
        if (this == object) {
            return true;
        }
        // 类型不同或对象为空时不相等。
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        // 转换为当前类型后逐字段比较。
        MessageContext other = (MessageContext) object;
        return Objects.equals(source, other.source)
                && Objects.equals(correlationId, other.correlationId)
                && Objects.equals(causationId, other.causationId)
                && Objects.equals(tenantId, other.tenantId);
    }

    /**
     * 根据全部字段计算哈希值。
     *
     * @return 哈希值
     */
    @Override
    public int hashCode() {
        // 使用与 equals 相同的字段计算哈希值。
        return Objects.hash(source, correlationId, causationId, tenantId);
    }

    /**
     * 返回便于诊断的字段摘要。
     *
     * @return 字符串摘要
     */
    @Override
    public String toString() {
        // 拼接全部字段，保持值对象可诊断。
        return "MessageContext{" +
                "source=" + source +
                ", correlationId=" + correlationId +
                ", causationId=" + causationId +
                ", tenantId=" + tenantId +
                '}';
    }

}
