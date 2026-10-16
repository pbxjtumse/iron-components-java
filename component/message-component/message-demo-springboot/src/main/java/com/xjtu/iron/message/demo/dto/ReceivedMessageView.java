package com.xjtu.iron.message.demo.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Spring Boot Demo 的已接收消息视图。
 */
public final class ReceivedMessageView {

    /** 实际消费到消息的 Provider。 */
    private final String providerName;

    /** Provider 物理目的地。 */
    private final String physicalDestination;

    /** 消费组或订阅名。 */
    private final String consumerGroup;

    /** Provider 原生消息 ID。 */
    private final String providerMessageId;

    /** 组件消息 ID。 */
    private final String messageId;

    /** 业务键。 */
    private final String businessKey;

    /** 业务消息类型。 */
    private final String eventType;

    /** 业务消息体。 */
    private final Object payload;

    /** 用户消息头。 */
    private final Map<String, String> headers;

    /** Demo 记录接收时间。 */
    private final Instant receivedAt;

    /**
     * 兼容旧调用的构造器。
     */
    public ReceivedMessageView(
            String messageId,
            String businessKey,
            String eventType,
            Object payload,
            Map<String, String> headers,
            Instant receivedAt) {
        this(null, null, null, null, messageId, businessKey, eventType, payload, headers, receivedAt);
    }

    public ReceivedMessageView(
            String providerName,
            String physicalDestination,
            String consumerGroup,
            String providerMessageId,
            String messageId,
            String businessKey,
            String eventType,
            Object payload,
            Map<String, String> headers,
            Instant receivedAt) {
        this.providerName = providerName;
        this.physicalDestination = physicalDestination;
        this.consumerGroup = consumerGroup;
        this.providerMessageId = providerMessageId;
        this.messageId = messageId;
        this.businessKey = businessKey;
        this.eventType = eventType;
        this.payload = payload;
        this.headers = headers;
        this.receivedAt = receivedAt;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getPhysicalDestination() {
        return physicalDestination;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public String getEventType() {
        return eventType;
    }

    public Object getPayload() {
        return payload;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
