package com.xjtu.iron.message.demo.dto;

import com.xjtu.iron.message.api.publish.SendReliabilityInfo;
import com.xjtu.iron.message.api.publish.SendResult;

/**
 * Spring Boot Demo 的发送响应对象。
 */
public final class SendMessageResponse {

    /** 实际使用的 Provider。 */
    private final String providerName;

    /** 组件消息 ID。 */
    private final String messageId;

    /** Provider 原生消息 ID。 */
    private final String providerMessageId;

    /** 逻辑目的地。 */
    private final String topic;

    /** Provider 物理目的地。 */
    private final String physicalDestination;

    /** 发送状态。 */
    private final String status;

    /** 失败类型。 */
    private final String failureType;

    /** 结果阶段。 */
    private final String stage;

    /** 诊断描述。 */
    private final String description;

    /** 是否启用可靠发送。 */
    private final boolean reliabilityEnabled;

    /** retryId。 */
    private final String retryId;

    /** retry 策略名称。 */
    private final String retryPolicy;

    /** retry 终态。 */
    private final String retryStatus;

    /** 尝试次数。 */
    private final int attempts;

    /** 最后失败码。 */
    private final String lastFailureCode;

    /** 最后失败分类。 */
    private final String lastFailureCategory;

    public SendMessageResponse(
            String messageId,
            String providerMessageId,
            String topic,
            String status) {
        this(null, messageId, providerMessageId, topic, null, status, null, null, null,
                false, null, null, null, 1, null, null);
    }

    public SendMessageResponse(
            String providerName,
            String messageId,
            String providerMessageId,
            String topic,
            String physicalDestination,
            String status,
            String failureType,
            String stage,
            String description,
            boolean reliabilityEnabled,
            String retryId,
            String retryPolicy,
            String retryStatus,
            int attempts,
            String lastFailureCode,
            String lastFailureCategory) {
        this.providerName = providerName;
        this.messageId = messageId;
        this.providerMessageId = providerMessageId;
        this.topic = topic;
        this.physicalDestination = physicalDestination;
        this.status = status;
        this.failureType = failureType;
        this.stage = stage;
        this.description = description;
        this.reliabilityEnabled = reliabilityEnabled;
        this.retryId = retryId;
        this.retryPolicy = retryPolicy;
        this.retryStatus = retryStatus;
        this.attempts = attempts;
        this.lastFailureCode = lastFailureCode;
        this.lastFailureCategory = lastFailureCategory;
    }

    /**
     * 从统一 SendResult 创建 Demo 响应。
     *
     * @param result 统一发送结果
     * @return Demo 响应
     */
    public static SendMessageResponse from(SendResult result) {
        SendReliabilityInfo reliabilityInfo = result.reliabilityInfo();
        return new SendMessageResponse(
                result.providerName(),
                result.messageId(),
                result.providerMessageId(),
                result.destination() == null ? null : result.destination().qualifiedName(),
                result.physicalDestination(),
                result.status() == null ? null : result.status().name(),
                result.failureType() == null ? null : result.failureType().name(),
                result.stage() == null ? null : result.stage().name(),
                result.description(),
                reliabilityInfo != null && reliabilityInfo.enabled(),
                reliabilityInfo == null ? null : reliabilityInfo.retryId(),
                reliabilityInfo == null ? null : reliabilityInfo.retryPolicy(),
                reliabilityInfo == null ? null : reliabilityInfo.retryStatus(),
                reliabilityInfo == null ? 1 : reliabilityInfo.attempts(),
                reliabilityInfo == null ? null : reliabilityInfo.lastFailureCode(),
                reliabilityInfo == null ? null : reliabilityInfo.lastFailureCategory());
    }

    /**
     * 创建 Demo 层异常响应。
     */
    public static SendMessageResponse failed(
            String providerName,
            String topic,
            String description) {
        return new SendMessageResponse(
                providerName,
                null,
                null,
                topic,
                null,
                "FAILED",
                "UNKNOWN_ERROR",
                null,
                description,
                false,
                null,
                null,
                null,
                1,
                null,
                null);
    }

    public String getProviderName() {
        return providerName;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public String getTopic() {
        return topic;
    }

    public String getPhysicalDestination() {
        return physicalDestination;
    }

    public String getStatus() {
        return status;
    }

    public String getFailureType() {
        return failureType;
    }

    public String getStage() {
        return stage;
    }

    public String getDescription() {
        return description;
    }

    public boolean isReliabilityEnabled() {
        return reliabilityEnabled;
    }

    public String getRetryId() {
        return retryId;
    }

    public String getRetryPolicy() {
        return retryPolicy;
    }

    public String getRetryStatus() {
        return retryStatus;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastFailureCode() {
        return lastFailureCode;
    }

    public String getLastFailureCategory() {
        return lastFailureCategory;
    }
}
