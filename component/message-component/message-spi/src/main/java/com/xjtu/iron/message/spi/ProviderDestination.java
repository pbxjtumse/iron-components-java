package com.xjtu.iron.message.spi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 表示逻辑目的地解析后的 Provider 物理目的地。
 *
 * <p>{@code providerName}：Provider 稳定名称</p>
 * <p>{@code physicalName}：物理 Topic 或同等目的地名称</p>
 * <p>{@code attributes}：路由配置中提供给 Provider 的扩展属性</p>
 */
public final class ProviderDestination {
    /** Provider 稳定名称。 */
    private final String providerName;

    /** 物理 Topic 或同等目的地名称。 */
    private final String physicalName;

    /** 路由配置中提供给 Provider 的扩展属性。 */
    private final Map<String, String> attributes;

    /**
     * 校验并复制物理目的地配置。
     */
    public ProviderDestination(
        String providerName,
        String physicalName,
        Map<String, String> attributes) {
        // Provider 名称必须存在。
        if (providerName == null || providerName.isBlank()) {
            // 没有 Provider 就无法完成发送或订阅。
            throw new IllegalArgumentException("providerName must not be blank");
        }
        // 去除 Provider 名称首尾空白。
        providerName = providerName.trim().toLowerCase(java.util.Locale.ROOT);
        // 物理目的地必须存在。
        if (physicalName == null || physicalName.isBlank()) {
            // 物理 Topic 为空时直接拒绝。
            throw new IllegalArgumentException("physicalName must not be blank");
        }
        // 去除物理名称首尾空白。
        physicalName = physicalName.trim();
        // 扩展属性执行防御性复制。
        attributes = attributes == null || attributes.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    
        // 保存完成校验和标准化后的 providerName。
        this.providerName = providerName;
        // 保存完成校验和标准化后的 physicalName。
        this.physicalName = physicalName;
        // 保存完成校验和标准化后的 attributes。
        this.attributes = attributes;
    }
    /**
     * 返回Provider 稳定名称。
     *
     * @return Provider 稳定名称
     */
    public String providerName() {
        // 返回不可变字段。
        return providerName;
    }

    /**
     * 返回物理 Topic 或同等目的地名称。
     *
     * @return 物理 Topic 或同等目的地名称
     */
    public String physicalName() {
        // 返回不可变字段。
        return physicalName;
    }

    /**
     * 返回路由配置中提供给 Provider 的扩展属性。
     *
     * @return 路由配置中提供给 Provider 的扩展属性
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
        ProviderDestination other = (ProviderDestination) object;
        return Objects.equals(providerName, other.providerName)
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
        return Objects.hash(providerName, physicalName, attributes);
    }

    /**
     * 返回便于诊断的字段摘要。
     *
     * @return 字符串摘要
     */
    @Override
    public String toString() {
        // 拼接全部字段，保持值对象可诊断。
        return "ProviderDestination{" +
                "providerName=" + providerName +
                ", physicalName=" + physicalName +
                ", attributes=" + attributes +
                '}';
    }

}
