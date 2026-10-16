package com.xjtu.iron.message.core.routing;

import com.xjtu.iron.message.api.model.MessageDestination;
import com.xjtu.iron.message.spi.ProviderDestination;

import java.util.*;

/**
 * 表示逻辑目的地到 Provider 物理目的地的精确路由。
 *
 * <p>{@code namespace}：逻辑命名空间</p>
 * <p>{@code name}：逻辑名称</p>
 * <p>{@code providerName}：Provider 名称</p>
 * <p>{@code physicalName}：物理 Topic 或同等名称</p>
 * <p>{@code attributes}：Provider 扩展路由属性</p>
 */
public final class DestinationRoute {
    /** 逻辑命名空间。 */
    private final String namespace;

    /** 逻辑名称。 */
    private final String name;

    /** Provider 名称。 */
    private final String providerName;

    /** 物理 Topic 或同等名称。 */
    private final String physicalName;

    /** Provider 扩展路由属性。 */
    private final Map<String, String> attributes;


    /** 校验并防御性复制。 */
    public DestinationRoute(
        String namespace,
        String name,
        String providerName,
        String physicalName,
        Map<String, String> attributes) {
        namespace = requireText(namespace, "namespace");
        name = requireText(name, "name");
        providerName = requireText(providerName, "providerName").toLowerCase(Locale.ROOT);
        physicalName = requireText(physicalName, "physicalName");
        attributes = attributes == null || attributes.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    
        // 保存完成校验和标准化后的 namespace。
        this.namespace = namespace;
        // 保存完成校验和标准化后的 name。
        this.name = name;
        // 保存完成校验和标准化后的 providerName。
        this.providerName = providerName;
        // 保存完成校验和标准化后的 physicalName。
        this.physicalName = physicalName;
        // 保存完成校验和标准化后的 attributes。
        this.attributes = attributes;
    }

    public static DestinationRoute of(
            MessageDestination destination,
            String providerName,
            String physicalName) {
        return of(destination, providerName, physicalName, Map.of());
    }

    public static DestinationRoute of(
            MessageDestination destination,
            String providerName,
            String physicalName,
            Map<String, String> attributes) {
        Objects.requireNonNull(destination, "destination must not be null");
        return new DestinationRoute(
                destination.namespace(),
                destination.name(),
                providerName,
                physicalName,
                attributes);
    }

    public boolean matches(MessageDestination destination) {
        return namespace.equals(destination.namespace()) && name.equals(destination.name());
    }

    public ProviderDestination toProviderDestination() {
        return new ProviderDestination(providerName, physicalName, attributes);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
    /**
     * 返回逻辑命名空间。
     *
     * @return 逻辑命名空间
     */
    public String namespace() {
        // 返回不可变字段。
        return namespace;
    }

    /**
     * 返回逻辑名称。
     *
     * @return 逻辑名称
     */
    public String name() {
        // 返回不可变字段。
        return name;
    }

    /**
     * 返回Provider 名称。
     *
     * @return Provider 名称
     */
    public String providerName() {
        // 返回不可变字段。
        return providerName;
    }

    /**
     * 返回物理 Topic 或同等名称。
     *
     * @return 物理 Topic 或同等名称
     */
    public String physicalName() {
        // 返回不可变字段。
        return physicalName;
    }

    /**
     * 返回Provider 扩展路由属性。
     *
     * @return Provider 扩展路由属性
     */
    public Map<String, String> attributes() {
        // 返回不可变字段。
        return attributes;
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
        DestinationRoute other = (DestinationRoute) object;
        return Objects.equals(namespace, other.namespace)
                && Objects.equals(name, other.name)
                && Objects.equals(providerName, other.providerName)
                && Objects.equals(physicalName, other.physicalName)
                && Objects.equals(attributes, other.attributes);
    }

    /**
     * 根据全部字段计算哈希值。
     *
     * @return 哈希值
     */
    @Override
    public int hashCode() {
        // 使用与 equals 相同的字段计算哈希值。
        return Objects.hash(namespace, name, providerName, physicalName, attributes);
    }

    /**
     * 返回便于诊断的字段摘要。
     *
     * @return 字符串摘要
     */
    @Override
    public String toString() {
        // 拼接全部字段，保持值对象可诊断。
        return "DestinationRoute{" +
                "namespace=" + namespace +
                ", name=" + name +
                ", providerName=" + providerName +
                ", physicalName=" + physicalName +
                ", attributes=" + attributes +
                '}';
    }

}
