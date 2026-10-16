package com.xjtu.iron.message.spi;

import com.xjtu.iron.message.api.publish.SendFailureType;
import com.xjtu.iron.message.api.publish.SendStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
/**
 * Provider 层发送结果，承接原生中间件结果到 message-core 统一语义的第一次映射。
 *
 * <p>Kafka、Pulsar、RocketMQ 的原生结果差异很大：有的返回 offset，有的返回 MessageId，有的返回 SendStatus。
 * Provider 必须先把这些差异压缩成 {@code SendStatus}、{@code SendFailureType}、providerMessageId 和 metadata，
 * 可靠发送层才能统一判断是否重试、是否 UNKNOWN、是否返回 CONFIRMED。</p>
 */
public final class ProviderSendResult {
    /** Provider 能够确认的发送状态。 */
    private final SendStatus status;

    /** 标准失败类型。 */
    private final SendFailureType failureType;

    /** Broker 或 Provider 原生消息 ID。 */
    private final String providerMessageId;

    /** 诊断描述。 */
    private final String description;

    /** 原生诊断元数据。 */
    private final Map<String, String> metadata;


    /**
     * 统一校验结果一致性并复制元数据。
     */
    public ProviderSendResult(
        SendStatus status,
        SendFailureType failureType,
        String providerMessageId,
        String description,
        Map<String, String> metadata) {
        // status 不能为空。
        status = Objects.requireNonNull(status, "status must not be null");
        // failureType 不能为空。
        failureType = Objects.requireNonNull(failureType, "failureType must not be null");
        // 成功确认时失败类型必须为 NONE。
        if (status == SendStatus.CONFIRMED && failureType != SendFailureType.NONE) {
            // 防止 Provider 返回互相矛盾的结果。
            throw new IllegalArgumentException("confirmed result must use NONE failureType");
        }
        // 非成功状态不能使用 NONE。
        if (status != SendStatus.CONFIRMED && failureType == SendFailureType.NONE) {
            // 失败原因缺失会降低治理可用性。
            throw new IllegalArgumentException("non-confirmed result must provide failureType");
        }
        // 元数据执行防御性复制。
        metadata = metadata == null || metadata.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    
        // 保存完成校验和标准化后的 status。
        this.status = status;
        // 保存完成校验和标准化后的 failureType。
        this.failureType = failureType;
        // 保存完成校验和标准化后的 providerMessageId。
        this.providerMessageId = providerMessageId;
        // 保存完成校验和标准化后的 description。
        this.description = description;
        // 保存完成校验和标准化后的 metadata。
        this.metadata = metadata;
    }

    /**
     * 创建明确成功结果。
     *
     * @param providerMessageId 原生消息 ID
     * @param metadata 原生元数据
     * @return 成功结果
     */
    public static ProviderSendResult confirmed(
            String providerMessageId,
            Map<String, String> metadata) {
        // 成功结果统一使用 NONE 失败类型。
        return new ProviderSendResult(
                SendStatus.CONFIRMED,
                SendFailureType.NONE,
                providerMessageId,
                null,
                metadata);
    }

    /**
     * 创建没有额外元数据的明确成功结果。
     *
     * @param providerMessageId 原生消息 ID
     * @return 成功结果
     */
    public static ProviderSendResult confirmed(String providerMessageId) {
        // 复用完整工厂方法。
        return confirmed(providerMessageId, Map.of());
    }

    /**
     * 创建非成功结果。
     *
     * @param status 状态
     * @param failureType 失败类型
     * @param description 诊断描述
     * @return 非成功结果
     */
    public static ProviderSendResult failed(
            SendStatus status,
            SendFailureType failureType,
            String description) {
        // 非成功工厂默认不设置原生消息 ID 和额外元数据。
        return failed(status, failureType, description, Map.of());
    }

    /**
     * 创建带 Provider 原生诊断元数据的非成功结果。
     *
     * <p>
     * 二期可靠发送会根据 status 和 failureType 决定是否重试，
     * 但排障时仍然需要保留 RocketMQ sendStatus、Kafka errorType、
     * Pulsar exceptionType 等原生信息。
     * </p>
     *
     * @param status 状态
     * @param failureType 失败类型
     * @param description 诊断描述
     * @param metadata Provider 原生诊断元数据
     * @return 非成功结果
     */
    public static ProviderSendResult failed(
            SendStatus status,
            SendFailureType failureType,
            String description,
            Map<String, String> metadata) {
        // 非成功工厂不设置原生消息 ID。
        return new ProviderSendResult(
                status,
                failureType,
                null,
                description,
                metadata);
    }
    /**
     * 返回Provider 能够确认的发送状态。
     *
     * @return Provider 能够确认的发送状态
     */
    public SendStatus status() {
        // 返回不可变字段。
        return status;
    }

    /**
     * 返回标准失败类型。
     *
     * @return 标准失败类型
     */
    public SendFailureType failureType() {
        // 返回不可变字段。
        return failureType;
    }

    /**
     * 返回Broker 或 Provider 原生消息 ID。
     *
     * @return Broker 或 Provider 原生消息 ID
     */
    public String providerMessageId() {
        // 返回不可变字段。
        return providerMessageId;
    }

    /**
     * 返回诊断描述。
     *
     * @return 诊断描述
     */
    public String description() {
        // 返回不可变字段。
        return description;
    }

    /**
     * 返回原生诊断元数据。
     *
     * @return 原生诊断元数据
     */
    public Map<String, String> metadata() {
        // 返回不可变字段。
        return metadata;
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
        ProviderSendResult other = (ProviderSendResult) object;
        return Objects.equals(status, other.status)
                && Objects.equals(failureType, other.failureType)
                && Objects.equals(providerMessageId, other.providerMessageId)
                && Objects.equals(description, other.description)
                && Objects.equals(metadata, other.metadata);
    }

    /**
     * 根据全部字段计算哈希值。
     *
     * @return 哈希值
     */
    @Override
    public int hashCode() {
        // 使用与 equals 相同的字段计算哈希值。
        return Objects.hash(status, failureType, providerMessageId, description, metadata);
    }

    /**
     * 返回便于诊断的字段摘要。
     *
     * @return 字符串摘要
     */
    @Override
    public String toString() {
        // 拼接全部字段，保持值对象可诊断。
        return "ProviderSendResult{" +
                "status=" + status +
                ", failureType=" + failureType +
                ", providerMessageId=" + providerMessageId +
                ", description=" + description +
                ", metadata=" + metadata +
                '}';
    }

}
