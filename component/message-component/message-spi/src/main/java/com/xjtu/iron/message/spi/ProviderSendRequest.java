package com.xjtu.iron.message.spi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * core 交给具体 Provider 的普通消息发送请求。
 *
 * <p>该类型使用普通不可变类显式声明 final 字段、构造参数校验和防御性复制，
 * 避免依赖 record 紧凑构造器的隐式字段赋值规则。</p>
 *
 * <p>{@code destination}：已解析物理目的地</p>
 * <p>{@code messageId}：组件消息 ID</p>
 * <p>{@code messageKey}：业务实体键；Provider 可映射为原生 key</p>
 * <p>{@code headers}：完整线级消息头</p>
 * <p>{@code body}：已序列化消息体</p>
 */
public final class ProviderSendRequest {
    /** 已解析物理目的地。 */
    private final ProviderDestination destination;

    /** 组件消息 ID。 */
    private final String messageId;

    /** 业务实体键；Provider 可映射为原生 key。 */
    private final String messageKey;

    /** 完整线级消息头。 */
    private final Map<String, String> headers;

    /** 已序列化消息体。 */
    private final byte[] body;


    /** 校验请求并防御性复制。 */
    public ProviderSendRequest(
        ProviderDestination destination,
        String messageId,
        String messageKey,
        Map<String, String> headers,
        byte[] body) {
        destination = Objects.requireNonNull(destination, "destination must not be null");
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("messageId must not be blank");
        }
        messageId = messageId.trim();
        messageKey = normalize(messageKey);
        headers = headers == null || headers.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        body = Objects.requireNonNull(body, "body must not be null").clone();
    
        // 保存完成校验和标准化后的 destination。
        this.destination = destination;
        // 保存完成校验和标准化后的 messageId。
        this.messageId = messageId;
        // 保存完成校验和标准化后的 messageKey。
        this.messageKey = messageKey;
        // 保存完成校验和标准化后的 headers。
        this.headers = headers;
        // 保存完成校验和标准化后的 body。
        this.body = body;
    }

    public byte[] body() {
        return body.clone();
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
    /**
     * 返回已解析物理目的地。
     *
     * @return 已解析物理目的地
     */
    public ProviderDestination destination() {
        // 返回不可变字段。
        return destination;
    }

    /**
     * 返回组件消息 ID。
     *
     * @return 组件消息 ID
     */
    public String messageId() {
        // 返回不可变字段。
        return messageId;
    }

    /**
     * 返回业务实体键；Provider 可映射为原生 key。
     *
     * @return 业务实体键；Provider 可映射为原生 key
     */
    public String messageKey() {
        // 返回不可变字段。
        return messageKey;
    }

    /**
     * 返回完整线级消息头。
     *
     * @return 完整线级消息头
     */
    public Map<String, String> headers() {
        // 返回不可变字段。
        return headers;
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
        ProviderSendRequest other = (ProviderSendRequest) object;
        return Objects.equals(destination, other.destination)
                && Objects.equals(messageId, other.messageId)
                && Objects.equals(messageKey, other.messageKey)
                && Objects.equals(headers, other.headers)
                && Objects.equals(body, other.body);
    }

    /**
     * 根据全部字段计算哈希值。
     *
     * @return 哈希值
     */
    @Override
    public int hashCode() {
        // 使用与 equals 相同的字段计算哈希值。
        return Objects.hash(destination, messageId, messageKey, headers, body);
    }

    /**
     * 返回便于诊断的字段摘要。
     *
     * @return 字符串摘要
     */
    @Override
    public String toString() {
        // 拼接全部字段，保持值对象可诊断。
        return "ProviderSendRequest{" +
                "destination=" + destination +
                ", messageId=" + messageId +
                ", messageKey=" + messageKey +
                ", headers=" + headers +
                ", body=" + body +
                '}';
    }

}
