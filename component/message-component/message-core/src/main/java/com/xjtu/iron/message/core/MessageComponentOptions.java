package com.xjtu.iron.message.core;

import com.xjtu.iron.message.core.routing.DestinationRoutingMode;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/**
 * 表示 message-core 的稳定运行参数。
 *
 * <p>{@code defaultProviderName}：未配置路由和 providerHint 时使用的默认 Provider</p>
 * <p>{@code applicationName}：默认消息来源；允许为空，此时 source 不会被强行伪造</p>
 * <p>{@code defaultSchemaVersion}：业务未声明版本时使用的默认结构版本</p>
 * <p>{@code defaultConfirmTimeout}：普通发送默认确认超时</p>
 * <p>{@code routingMode}：未配置精确路由时的处理策略</p>
 * <p>{@code clock}：生成消息时间和结果时间使用的统一时钟</p>
 */
public final class MessageComponentOptions {
    /** 未配置路由和 providerHint 时使用的默认 Provider。 */
    private final String defaultProviderName;

    /** 默认消息来源；允许为空，此时 source 不会被强行伪造。 */
    private final String applicationName;

    /** 业务未声明版本时使用的默认结构版本。 */
    private final String defaultSchemaVersion;

    /** 普通发送默认确认超时。 */
    private final Duration defaultConfirmTimeout;

    /** 未配置精确路由时的处理策略。 */
    private final DestinationRoutingMode routingMode;

    /** 生成消息时间和结果时间使用的统一时钟。 */
    private final Clock clock;


    /**
     * 校验并标准化组件参数。
     */
    public MessageComponentOptions(
        String defaultProviderName,
        String applicationName,
        String defaultSchemaVersion,
        Duration defaultConfirmTimeout,
        DestinationRoutingMode routingMode,
        Clock clock) {
        // 默认 Provider 必须存在，否则无路由消息无法发送。
        defaultProviderName = requireText(defaultProviderName, "defaultProviderName").toLowerCase(java.util.Locale.ROOT);
        // applicationName 是可选值，空白统一转为 null。
        applicationName = normalize(applicationName);
        // 默认结构版本必须存在。
        defaultSchemaVersion = requireText(defaultSchemaVersion, "defaultSchemaVersion");
        // 默认确认超时不能为空。
        defaultConfirmTimeout = Objects.requireNonNull(
                defaultConfirmTimeout,
                "defaultConfirmTimeout must not be null");
        // 确认超时必须为正数。
        if (defaultConfirmTimeout.isZero() || defaultConfirmTimeout.isNegative()) {
            // 非正时间无法形成合理等待窗口。
            throw new IllegalArgumentException("defaultConfirmTimeout must be positive");
        }
        // 路由模式不能为空；生产默认应使用严格路由。
        routingMode = Objects.requireNonNull(routingMode, "routingMode must not be null");
        // 时钟不能为空，测试可传入固定时钟。
        clock = Objects.requireNonNull(clock, "clock must not be null");
    
        // 保存完成校验和标准化后的 defaultProviderName。
        this.defaultProviderName = defaultProviderName;
        // 保存完成校验和标准化后的 applicationName。
        this.applicationName = applicationName;
        // 保存完成校验和标准化后的 defaultSchemaVersion。
        this.defaultSchemaVersion = defaultSchemaVersion;
        // 保存完成校验和标准化后的 defaultConfirmTimeout。
        this.defaultConfirmTimeout = defaultConfirmTimeout;
        // 保存完成校验和标准化后的 routingMode。
        this.routingMode = routingMode;
        // 保存完成校验和标准化后的 clock。
        this.clock = clock;
    }

    /**
     * 创建常用默认参数。
     *
     * @param defaultProviderName 默认 Provider
     * @param applicationName 应用名称
     * @return 默认参数
     */
    public static MessageComponentOptions defaults(
            String defaultProviderName,
            String applicationName) {
        // 默认使用结构版本 1、三秒确认超时和系统 UTC 时钟。
        return new MessageComponentOptions(
                defaultProviderName,
                applicationName,
                "1",
                Duration.ofSeconds(3),
                DestinationRoutingMode.STRICT,
                Clock.systemUTC());
    }

    /** 标准化可选文本。 */
    private static String normalize(String value) {
        // null 保持 null。
        if (value == null) {
            // 返回 null 表示未配置。
            return null;
        }
        // 去除首尾空白。
        String trimmed = value.trim();
        // 空白字符串转换为 null。
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 校验必填文本。 */
    private static String requireText(String value, String fieldName) {
        // 先执行统一标准化。
        String normalized = normalize(value);
        // 必填字段为空时立即失败。
        if (normalized == null) {
            // 明确指出非法字段。
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        // 返回合法值。
        return normalized;
    }
    /**
     * 返回未配置路由和 providerHint 时使用的默认 Provider。
     *
     * @return 未配置路由和 providerHint 时使用的默认 Provider
     */
    public String defaultProviderName() {
        // 返回不可变字段。
        return defaultProviderName;
    }

    /**
     * 返回默认消息来源；允许为空，此时 source 不会被强行伪造。
     *
     * @return 默认消息来源；允许为空，此时 source 不会被强行伪造
     */
    public String applicationName() {
        // 返回不可变字段。
        return applicationName;
    }

    /**
     * 返回业务未声明版本时使用的默认结构版本。
     *
     * @return 业务未声明版本时使用的默认结构版本
     */
    public String defaultSchemaVersion() {
        // 返回不可变字段。
        return defaultSchemaVersion;
    }

    /**
     * 返回普通发送默认确认超时。
     *
     * @return 普通发送默认确认超时
     */
    public Duration defaultConfirmTimeout() {
        // 返回不可变字段。
        return defaultConfirmTimeout;
    }

    /**
     * 返回未配置精确路由时的处理策略。
     *
     * @return 未配置精确路由时的处理策略
     */
    public DestinationRoutingMode routingMode() {
        // 返回不可变字段。
        return routingMode;
    }

    /**
     * 返回生成消息时间和结果时间使用的统一时钟。
     *
     * @return 生成消息时间和结果时间使用的统一时钟
     */
    public Clock clock() {
        // 返回不可变字段。
        return clock;
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
        MessageComponentOptions other = (MessageComponentOptions) object;
        return Objects.equals(defaultProviderName, other.defaultProviderName)
                && Objects.equals(applicationName, other.applicationName)
                && Objects.equals(defaultSchemaVersion, other.defaultSchemaVersion)
                && Objects.equals(defaultConfirmTimeout, other.defaultConfirmTimeout)
                && Objects.equals(routingMode, other.routingMode)
                && Objects.equals(clock, other.clock);
    }

    /**
     * 根据全部字段计算哈希值。
     *
     * @return 哈希值
     */
    @Override
    public int hashCode() {
        // 使用与 equals 相同的字段计算哈希值。
        return Objects.hash(defaultProviderName, applicationName, defaultSchemaVersion, defaultConfirmTimeout, routingMode, clock);
    }

    /**
     * 返回便于诊断的字段摘要。
     *
     * @return 字符串摘要
     */
    @Override
    public String toString() {
        // 拼接全部字段，保持值对象可诊断。
        return "MessageComponentOptions{" +
                "defaultProviderName=" + defaultProviderName +
                ", applicationName=" + applicationName +
                ", defaultSchemaVersion=" + defaultSchemaVersion +
                ", defaultConfirmTimeout=" + defaultConfirmTimeout +
                ", routingMode=" + routingMode +
                ", clock=" + clock +
                '}';
    }

}
