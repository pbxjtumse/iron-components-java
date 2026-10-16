package com.xjtu.iron.message.api.publish;

import com.xjtu.iron.message.api.model.MessageDestination;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
/**
 * 业务发送调用最终拿到的统一结果对象。
 *
 * <p>{@code SendResult} 不只是简单成功或失败。二期可靠发送之后，它需要同时表达四类信息：</p>
 * <ol>
 *   <li>消息身份：messageId、destination、providerName、providerMessageId；</li>
 *   <li>发送结论：CONFIRMED、FAILED、REJECTED、UNKNOWN；</li>
 *   <li>失败语义：失败阶段、失败类型、描述、Provider 元数据；</li>
 *   <li>可靠性信息：retryId、retryPolicy、retryStatus、attempts、lastFailureCode。</li>
 * </ol>
 *
 * <p>特别注意 UNKNOWN：它表示组件无法确认 Broker 是否已经收到消息，不能被业务简单当成 FAILED 立即重发。</p>
 */
public final class SendResult {

    /** 组件消息 ID。 */
    private final String messageId;

    /** 逻辑目的地。 */
    private final MessageDestination destination;

    /** 实际使用的 Provider。 */
    private final String providerName;

    /** 实际物理目的地。 */
    private final String physicalDestination;

    /** 发送状态。 */
    private final SendStatus status;

    /** 结果产生阶段。 */
    private final SendStage stage;

    /** 标准失败类型。 */
    private final SendFailureType failureType;

    /** Provider 或 Broker 返回的原生消息 ID。 */
    private final String providerMessageId;

    /** 诊断描述。 */
    private final String description;

    /** 发送开始时间。 */
    private final Instant startedAt;

    /** 发送完成时间。 */
    private final Instant completedAt;

    /** Provider 返回的只读诊断元数据。 */
    private final Map<String, String> metadata;

    /** 发送可靠性执行信息。 */
    private final SendReliabilityInfo reliabilityInfo;

    /**
     * 兼容一期的构造器。
     */
    public SendResult(
            String messageId,
            MessageDestination destination,
            String providerName,
            String physicalDestination,
            SendStatus status,
            SendStage stage,
            SendFailureType failureType,
            String providerMessageId,
            String description,
            Instant startedAt,
            Instant completedAt,
            Map<String, String> metadata) {
        this(
                messageId,
                destination,
                providerName,
                physicalDestination,
                status,
                stage,
                failureType,
                providerMessageId,
                description,
                startedAt,
                completedAt,
                metadata,
                SendReliabilityInfo.disabled());
    }

    /**
     * 创建完整发送结果。
     */
    public SendResult(
            String messageId,
            MessageDestination destination,
            String providerName,
            String physicalDestination,
            SendStatus status,
            SendStage stage,
            SendFailureType failureType,
            String providerMessageId,
            String description,
            Instant startedAt,
            Instant completedAt,
            Map<String, String> metadata,
            SendReliabilityInfo reliabilityInfo) {
        this.messageId = messageId;
        this.destination = destination;
        this.providerName = providerName;
        this.physicalDestination = physicalDestination;
        this.status = status;
        this.stage = stage;
        this.failureType = failureType;
        this.providerMessageId = providerMessageId;
        this.description = description;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.metadata = metadata == null || metadata.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        this.reliabilityInfo = reliabilityInfo == null
                ? SendReliabilityInfo.disabled()
                : reliabilityInfo;
    }

    /**
     * 判断消息是否已经获得明确成功确认。
     *
     * @return 已确认时返回 true
     */
    public boolean confirmed() {
        return status == SendStatus.CONFIRMED;
    }

    /**
     * 判断结果是否不确定。
     *
     * @return 不确定时返回 true
     */
    public boolean unknown() {
        return status == SendStatus.UNKNOWN;
    }

    public String messageId() {
        return messageId;
    }

    public MessageDestination destination() {
        return destination;
    }

    public String providerName() {
        return providerName;
    }

    public String physicalDestination() {
        return physicalDestination;
    }

    public SendStatus status() {
        return status;
    }

    public SendStage stage() {
        return stage;
    }

    public SendFailureType failureType() {
        return failureType;
    }

    public String providerMessageId() {
        return providerMessageId;
    }

    public String description() {
        return description;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public Map<String, String> metadata() {
        return metadata;
    }

    public SendReliabilityInfo reliabilityInfo() {
        return reliabilityInfo;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        SendResult other = (SendResult) object;
        return Objects.equals(messageId, other.messageId)
                && Objects.equals(destination, other.destination)
                && Objects.equals(providerName, other.providerName)
                && Objects.equals(physicalDestination, other.physicalDestination)
                && status == other.status
                && stage == other.stage
                && failureType == other.failureType
                && Objects.equals(providerMessageId, other.providerMessageId)
                && Objects.equals(description, other.description)
                && Objects.equals(startedAt, other.startedAt)
                && Objects.equals(completedAt, other.completedAt)
                && Objects.equals(metadata, other.metadata)
                && Objects.equals(reliabilityInfo, other.reliabilityInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                messageId,
                destination,
                providerName,
                physicalDestination,
                status,
                stage,
                failureType,
                providerMessageId,
                description,
                startedAt,
                completedAt,
                metadata,
                reliabilityInfo);
    }

    @Override
    public String toString() {
        return "SendResult{"
                + "messageId=" + messageId
                + ", destination=" + destination
                + ", providerName=" + providerName
                + ", physicalDestination=" + physicalDestination
                + ", status=" + status
                + ", stage=" + stage
                + ", failureType=" + failureType
                + ", providerMessageId=" + providerMessageId
                + ", description=" + description
                + ", startedAt=" + startedAt
                + ", completedAt=" + completedAt
                + ", metadata=" + metadata
                + ", reliabilityInfo=" + reliabilityInfo
                + '}';
    }
}
