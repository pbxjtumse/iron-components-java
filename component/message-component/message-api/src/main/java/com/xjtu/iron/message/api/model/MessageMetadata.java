package com.xjtu.iron.message.api.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 描述一条消息自身的稳定元数据。
 *
 * <p>messageId 标识消息实例；messageKey 标识消息主要关联的业务实体。两者语义不同，
 * 同一订单产生多条消息时 messageId 不同，而 messageKey 可以都等于同一个订单号。</p>
 *
 * <p>{@code messageId}：消息实例唯一标识；发送前允许为空，由 core 生成</p>
 * <p>{@code messageType}：稳定业务消息类型，例如 OrderPaid</p>
 * <p>{@code schemaVersion}：消息结构版本；发送前允许为空，由 core 补齐</p>
 * <p>{@code messageKey}：主要业务实体标识；可用于 Provider 的键路由或检索，但不承诺顺序</p>
 * <p>{@code occurredAt}：业务事实实际发生时间；发送前允许为空</p>
 * <p>{@code createdAt}：消息信封创建时间；发送前允许为空，由 core 补齐</p>
 */
public final class MessageMetadata {
    /** 消息实例唯一标识；发送前允许为空，由 core 生成。 */
    private final String messageId;

    /** 稳定业务消息类型，例如 OrderPaid。 */
    private final String messageType;

    /** 消息结构版本；发送前允许为空，由 core 补齐。 */
    private final String schemaVersion;

    /** 主要业务实体标识；可用于 Provider 的键路由或检索，但不承诺顺序。 */
    private final String messageKey;

    /** 业务事实实际发生时间；发送前允许为空。 */
    private final Instant occurredAt;

    /** 消息信封创建时间；发送前允许为空，由 core 补齐。 */
    private final Instant createdAt;


    /** 标准化字符串并校验消息类型。 */
    public MessageMetadata(
        String messageId,
        String messageType,
        String schemaVersion,
        String messageKey,
        Instant occurredAt,
        Instant createdAt) {
        messageId = normalize(messageId);
        messageType = requireText(messageType, "messageType");
        schemaVersion = normalize(schemaVersion);
        messageKey = normalize(messageKey);
    
        // 保存完成校验和标准化后的 messageId。
        this.messageId = messageId;
        // 保存完成校验和标准化后的 messageType。
        this.messageType = messageType;
        // 保存完成校验和标准化后的 schemaVersion。
        this.schemaVersion = schemaVersion;
        // 保存完成校验和标准化后的 messageKey。
        this.messageKey = messageKey;
        // 保存完成校验和标准化后的 occurredAt。
        this.occurredAt = occurredAt;
        // 保存完成校验和标准化后的 createdAt。
        this.createdAt = createdAt;
    }

    /** 创建只包含消息类型的待丰富元数据。 */
    public static MessageMetadata pending(String messageType) {
        return new MessageMetadata(null, messageType, null, null, null, null);
    }

    /** 返回替换 core 管理字段后的新元数据。 */
    public MessageMetadata enriched(
            String enrichedMessageId,
            String enrichedSchemaVersion,
            Instant enrichedOccurredAt,
            Instant enrichedCreatedAt) {
        return new MessageMetadata(
                enrichedMessageId,
                messageType,
                enrichedSchemaVersion,
                messageKey,
                enrichedOccurredAt,
                enrichedCreatedAt);
    }

    private static String requireText(String value, String fieldName) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
    /**
     * 返回消息实例唯一标识；发送前允许为空，由 core 生成。
     *
     * @return 消息实例唯一标识；发送前允许为空，由 core 生成
     */
    public String messageId() {
        // 返回不可变字段。
        return messageId;
    }

    /**
     * 返回稳定业务消息类型，例如 OrderPaid。
     *
     * @return 稳定业务消息类型，例如 OrderPaid
     */
    public String messageType() {
        // 返回不可变字段。
        return messageType;
    }

    /**
     * 返回消息结构版本；发送前允许为空，由 core 补齐。
     *
     * @return 消息结构版本；发送前允许为空，由 core 补齐
     */
    public String schemaVersion() {
        // 返回不可变字段。
        return schemaVersion;
    }

    /**
     * 返回主要业务实体标识；可用于 Provider 的键路由或检索，但不承诺顺序。
     *
     * @return 主要业务实体标识；可用于 Provider 的键路由或检索，但不承诺顺序
     */
    public String messageKey() {
        // 返回不可变字段。
        return messageKey;
    }

    /**
     * 返回业务事实实际发生时间；发送前允许为空。
     *
     * @return 业务事实实际发生时间；发送前允许为空
     */
    public Instant occurredAt() {
        // 返回不可变字段。
        return occurredAt;
    }

    /**
     * 返回消息信封创建时间；发送前允许为空，由 core 补齐。
     *
     * @return 消息信封创建时间；发送前允许为空，由 core 补齐
     */
    public Instant createdAt() {
        // 返回不可变字段。
        return createdAt;
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
        MessageMetadata other = (MessageMetadata) object;
        return Objects.equals(messageId, other.messageId)
                && Objects.equals(messageType, other.messageType)
                && Objects.equals(schemaVersion, other.schemaVersion)
                && Objects.equals(messageKey, other.messageKey)
                && Objects.equals(occurredAt, other.occurredAt)
                && Objects.equals(createdAt, other.createdAt);
    }

    /**
     * 根据全部字段计算哈希值。
     *
     * @return 哈希值
     */
    @Override
    public int hashCode() {
        // 使用与 equals 相同的字段计算哈希值。
        return Objects.hash(messageId, messageType, schemaVersion, messageKey, occurredAt, createdAt);
    }

    /**
     * 返回便于诊断的字段摘要。
     *
     * @return 字符串摘要
     */
    @Override
    public String toString() {
        // 拼接全部字段，保持值对象可诊断。
        return "MessageMetadata{" +
                "messageId=" + messageId +
                ", messageType=" + messageType +
                ", schemaVersion=" + schemaVersion +
                ", messageKey=" + messageKey +
                ", occurredAt=" + occurredAt +
                ", createdAt=" + createdAt +
                '}';
    }

}
