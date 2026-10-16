package com.xjtu.iron.message.integration.rocketmq.config;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * RocketMQ 4.x Remoting Java Client 的一期基础配置。
 *
 * <p>{@code nameServer}：RocketMQ NameServer 地址</p>
 * <p>{@code producerGroup}：Producer 组名</p>
 * <p>{@code topics}：当前 Provider 允许发送和订阅的物理 Topic</p>
 * <p>{@code sendTimeout}：同步发送等待 Broker 返回的超时时间</p>
 * <p>{@code vipChannelEnabled}：是否启用 10909 VIP Channel</p>
 */
public final class RocketMqMessageProviderConfig {

    /** RocketMQ NameServer 地址。 */
    private final String nameServer;

    /** Producer 组名。 */
    private final String producerGroup;

    /** 当前 Provider 允许发送和订阅的物理 Topic。 */
    private final Set<String> topics;

    /** 同步发送超时。 */
    private final Duration sendTimeout;

    /** 同步发送失败后的重试次数。 */
    private final int retryTimesWhenSendFailed;

    /** 异步发送失败后的重试次数。 */
    private final int retryTimesWhenSendAsyncFailed;

    /** 是否启用 VIP Channel。 */
    private final boolean vipChannelEnabled;

    /** Consumer 从哪里开始消费。 */
    private final String consumeFromWhere;

    /** Tag 过滤表达式。 */
    private final String tagExpression;

    /** 可选访问密钥，当前一期本地验证不启用。 */
    private final String accessKey;

    /** 可选访问密钥，当前一期本地验证不启用。 */
    private final String secretKey;

    /**
     * 校验并复制 RocketMQ 配置。
     */
    public RocketMqMessageProviderConfig(
            String nameServer,
            String producerGroup,
            Set<String> topics,
            Duration sendTimeout,
            int retryTimesWhenSendFailed,
            int retryTimesWhenSendAsyncFailed,
            boolean vipChannelEnabled,
            String consumeFromWhere,
            String tagExpression,
            String accessKey,
            String secretKey) {
        if (nameServer == null || nameServer.isBlank()) {
            throw new IllegalArgumentException("nameServer must not be blank");
        }
        nameServer = nameServer.trim();
        if (producerGroup == null || producerGroup.isBlank()) {
            throw new IllegalArgumentException("producerGroup must not be blank");
        }
        producerGroup = producerGroup.trim();
        if (topics == null || topics.isEmpty()) {
            throw new IllegalArgumentException("at least one RocketMQ topic is required");
        }
        LinkedHashSet<String> normalizedTopics = new LinkedHashSet<>();
        for (String topic : topics) {
            if (topic == null || topic.isBlank()) {
                throw new IllegalArgumentException("RocketMQ topic must not be blank");
            }
            normalizedTopics.add(topic.trim());
        }
        topics = Set.copyOf(normalizedTopics);
        if (sendTimeout == null || sendTimeout.isZero() || sendTimeout.isNegative()) {
            throw new IllegalArgumentException("sendTimeout must be positive");
        }
        if (retryTimesWhenSendFailed < 0) {
            throw new IllegalArgumentException("retryTimesWhenSendFailed must not be negative");
        }
        if (retryTimesWhenSendAsyncFailed < 0) {
            throw new IllegalArgumentException("retryTimesWhenSendAsyncFailed must not be negative");
        }
        consumeFromWhere = normalizeOrDefault(consumeFromWhere, "CONSUME_FROM_LAST_OFFSET");
        tagExpression = normalizeOrDefault(tagExpression, "*");
        boolean accessKeyPresent = accessKey != null && !accessKey.isBlank();
        boolean secretKeyPresent = secretKey != null && !secretKey.isBlank();
        if (accessKeyPresent != secretKeyPresent) {
            throw new IllegalArgumentException("accessKey and secretKey must be configured together");
        }
        accessKey = accessKeyPresent ? accessKey.trim() : null;
        secretKey = secretKeyPresent ? secretKey.trim() : null;

        this.nameServer = nameServer;
        this.producerGroup = producerGroup;
        this.topics = topics;
        this.sendTimeout = sendTimeout;
        this.retryTimesWhenSendFailed = retryTimesWhenSendFailed;
        this.retryTimesWhenSendAsyncFailed = retryTimesWhenSendAsyncFailed;
        this.vipChannelEnabled = vipChannelEnabled;
        this.consumeFromWhere = consumeFromWhere;
        this.tagExpression = tagExpression;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    /** 创建无鉴权默认配置。 */
    public static RocketMqMessageProviderConfig defaults(String nameServer, Set<String> topics) {
        return new RocketMqMessageProviderConfig(
                nameServer,
                "message-producer-group",
                topics,
                Duration.ofSeconds(3),
                2,
                2,
                false,
                "CONSUME_FROM_LAST_OFFSET",
                "*",
                null,
                null);
    }

    private static String normalizeOrDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    public String nameServer() {
        return nameServer;
    }

    public String producerGroup() {
        return producerGroup;
    }

    public Set<String> topics() {
        return topics;
    }

    public Duration sendTimeout() {
        return sendTimeout;
    }

    public int retryTimesWhenSendFailed() {
        return retryTimesWhenSendFailed;
    }

    public int retryTimesWhenSendAsyncFailed() {
        return retryTimesWhenSendAsyncFailed;
    }

    public boolean vipChannelEnabled() {
        return vipChannelEnabled;
    }

    public String consumeFromWhere() {
        return consumeFromWhere;
    }

    public String tagExpression() {
        return tagExpression;
    }

    public String accessKey() {
        return accessKey;
    }

    public String secretKey() {
        return secretKey;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        RocketMqMessageProviderConfig other = (RocketMqMessageProviderConfig) object;
        return retryTimesWhenSendFailed == other.retryTimesWhenSendFailed
                && retryTimesWhenSendAsyncFailed == other.retryTimesWhenSendAsyncFailed
                && vipChannelEnabled == other.vipChannelEnabled
                && Objects.equals(nameServer, other.nameServer)
                && Objects.equals(producerGroup, other.producerGroup)
                && Objects.equals(topics, other.topics)
                && Objects.equals(sendTimeout, other.sendTimeout)
                && Objects.equals(consumeFromWhere, other.consumeFromWhere)
                && Objects.equals(tagExpression, other.tagExpression)
                && Objects.equals(accessKey, other.accessKey)
                && Objects.equals(secretKey, other.secretKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                nameServer,
                producerGroup,
                topics,
                sendTimeout,
                retryTimesWhenSendFailed,
                retryTimesWhenSendAsyncFailed,
                vipChannelEnabled,
                consumeFromWhere,
                tagExpression,
                accessKey,
                secretKey);
    }

    @Override
    public String toString() {
        return "RocketMqMessageProviderConfig{" +
                "nameServer=" + nameServer +
                ", producerGroup=" + producerGroup +
                ", topics=" + topics +
                ", sendTimeout=" + sendTimeout +
                ", retryTimesWhenSendFailed=" + retryTimesWhenSendFailed +
                ", retryTimesWhenSendAsyncFailed=" + retryTimesWhenSendAsyncFailed +
                ", vipChannelEnabled=" + vipChannelEnabled +
                ", consumeFromWhere=" + consumeFromWhere +
                ", tagExpression=" + tagExpression +
                ", authenticationConfigured=" + (accessKey != null) +
                '}';
    }
}
