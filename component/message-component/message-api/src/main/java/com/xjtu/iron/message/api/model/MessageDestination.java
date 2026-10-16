package com.xjtu.iron.message.api.model;

import java.util.Locale;
import java.util.Objects;

/**
 * 表示业务使用的【逻辑消息目的地】。
 *
 * <p>Event 或 Command 属于消息命名和业务契约规范，不再作为一期强制分类字段。Provider 和物理 Topic 由 core 路由解析。</p>
 *
 * <p>{@code namespace}：业务域或限界上下文，例如 trade</p>
 * <p>{@code name}：逻辑消息名称，例如 order-paid 或 close-order</p>
 * <p>{@code providerHint}：可选 Provider 覆盖提示</p>
 */
public final class MessageDestination {
    /** 业务域或限界上下文，例如 trade。 */
    private final String namespace;

    /** 逻辑消息名称，例如 order-paid 或 close-order。 */
    private final String name;

    /** 可选 Provider 覆盖提示。 */
    private final String providerHint;


    /** 校验并标准化逻辑目的地。 */
    public MessageDestination(
        String namespace,
        String name,
        String providerHint) {
        namespace = requireText(namespace, "namespace");
        name = requireText(name, "name");
        providerHint = normalizeProvider(providerHint);
    
        // 保存完成校验和标准化后的 namespace。
        this.namespace = namespace;
        // 保存完成校验和标准化后的 name。
        this.name = name;
        // 保存完成校验和标准化后的 providerHint。
        this.providerHint = providerHint;
    }

    /** 创建不指定 Provider 的逻辑目的地。 */
    public static MessageDestination of(String namespace, String name) {
        return new MessageDestination(namespace, name, null);
    }

    /** 返回带 Provider 提示的新目的地。 */
    public MessageDestination withProviderHint(String providerName) {
        return new MessageDestination(namespace, name, providerName);
    }

    /** 返回稳定逻辑名称。 */
    public String qualifiedName() {
        return namespace + ":" + name;
    }

    private static String requireText(String value, String fieldName) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private static String normalizeProvider(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
    /**
     * 返回业务域或限界上下文，例如 trade。
     *
     * @return 业务域或限界上下文，例如 trade
     */
    public String namespace() {
        // 返回不可变字段。
        return namespace;
    }

    /**
     * 返回逻辑消息名称，例如 order-paid 或 close-order。
     *
     * @return 逻辑消息名称，例如 order-paid 或 close-order
     */
    public String name() {
        // 返回不可变字段。
        return name;
    }

    /**
     * 返回可选 Provider 覆盖提示。
     *
     * @return 可选 Provider 覆盖提示
     */
    public String providerHint() {
        // 返回不可变字段。
        return providerHint;
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
        MessageDestination other = (MessageDestination) object;
        return Objects.equals(namespace, other.namespace)
                && Objects.equals(name, other.name)
                && Objects.equals(providerHint, other.providerHint);
    }

    /**
     * 根据全部字段计算哈希值。
     *
     * @return 哈希值
     */
    @Override
    public int hashCode() {
        // 使用与 equals 相同的字段计算哈希值。
        return Objects.hash(namespace, name, providerHint);
    }

    /**
     * 返回便于诊断的字段摘要。
     *
     * @return 字符串摘要
     */
    @Override
    public String toString() {
        // 拼接全部字段，保持值对象可诊断。
        return "MessageDestination{" +
                "namespace=" + namespace +
                ", name=" + name +
                ", providerHint=" + providerHint +
                '}';
    }

}
