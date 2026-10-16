package com.xjtu.iron.message.integration.rocketmq.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * RocketMQ Provider 的 Spring Boot 配置属性。
 *
 * <p>当前阶段面向 RocketMQ 4.x Remoting Client，也就是通过 NameServer 和 Broker 直连的模式。
 * 这与 RocketMQ 5.x gRPC Proxy 模式不同，不能把 9876 NameServer 当成 gRPC endpoint 使用。</p>
 */
@ConfigurationProperties(prefix = "xjtu.iron.message.rocketmq")
/**
 * RocketMQ4 Provider 的配置属性模型。
 *
 * <p>这里保存 RocketMQ Remoting 客户端所需参数，例如 NameServer 地址、生产者组、消费者组、topic 列表和发送超时。
 * 它不表达 RocketMQ5 gRPC 语义。</p>
 */
public class RocketMqMessageProperties {

    /** 是否启用 RocketMQ Provider 自动配置。 */
    private boolean enabled = false;

    /**
     * RocketMQ NameServer 地址。
     *
     * <p>多个地址使用分号分隔，例如 {@code host1:9876;host2:9876}。</p>
     */
    private String nameServer = "localhost:9876";

    /** Producer 组名。 */
    private String producerGroup = "message-producer-group";

    /** 默认 Consumer 组名；Demo 仍优先使用 {@code xjtu.iron.message.demo.consumer-group}。 */
    private String consumerGroup = "message-consumer-group";

    /** Provider 启动时预声明的 Topic。为空时由通用 routes 中 provider=rocketmq 的 physical-name 自动推导。 */
    private List<String> topics = new ArrayList<>();

    /** 同步发送超时。 */
    private Duration sendTimeout = Duration.ofSeconds(3);

    /** RocketMQ 客户端一次请求超时，主要用于后续扩展；当前 4.x Provider 以 sendTimeout 为准。 */
    private Duration requestTimeout = Duration.ofSeconds(3);

    /** 同步发送失败后的客户端重试次数。 */
    private int retryTimesWhenSendFailed = 2;

    /** 异步发送失败后的客户端重试次数。 */
    private int retryTimesWhenSendAsyncFailed = 2;

    /**
     * 是否启用 RocketMQ VIP Channel。
     *
     * <p>RocketMQ 4.x 默认可能访问 brokerPort - 2，即 10909。集群外验证时建议关闭，
     * 避免 NameServer 返回 10911 后客户端实际访问 10909 导致连接失败。</p>
     */
    private boolean vipChannelEnabled = false;

    /** Consumer 从哪里开始消费。可选 CONSUME_FROM_LAST_OFFSET、CONSUME_FROM_FIRST_OFFSET。 */
    private String consumeFromWhere = "CONSUME_FROM_LAST_OFFSET";

    /** Tag 过滤表达式；一期默认订阅全部 Tag。 */
    private String tagExpression = "*";

    /** ACL AccessKey；当前本地验证默认不配置。 */
    private String accessKey;

    /** ACL SecretKey；当前本地验证默认不配置。 */
    private String secretKey;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getNameServer() {
        return nameServer;
    }

    public void setNameServer(String nameServer) {
        this.nameServer = nameServer;
    }

    public String getProducerGroup() {
        return producerGroup;
    }

    public void setProducerGroup(String producerGroup) {
        this.producerGroup = producerGroup;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public List<String> getTopics() {
        return topics;
    }

    public void setTopics(List<String> topics) {
        this.topics = topics == null ? new ArrayList<>() : topics;
    }

    public Duration getSendTimeout() {
        return sendTimeout;
    }

    public void setSendTimeout(Duration sendTimeout) {
        this.sendTimeout = sendTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public int getRetryTimesWhenSendFailed() {
        return retryTimesWhenSendFailed;
    }

    public void setRetryTimesWhenSendFailed(int retryTimesWhenSendFailed) {
        this.retryTimesWhenSendFailed = retryTimesWhenSendFailed;
    }

    public int getRetryTimesWhenSendAsyncFailed() {
        return retryTimesWhenSendAsyncFailed;
    }

    public void setRetryTimesWhenSendAsyncFailed(int retryTimesWhenSendAsyncFailed) {
        this.retryTimesWhenSendAsyncFailed = retryTimesWhenSendAsyncFailed;
    }

    public boolean isVipChannelEnabled() {
        return vipChannelEnabled;
    }

    public void setVipChannelEnabled(boolean vipChannelEnabled) {
        this.vipChannelEnabled = vipChannelEnabled;
    }

    public String getConsumeFromWhere() {
        return consumeFromWhere;
    }

    public void setConsumeFromWhere(String consumeFromWhere) {
        this.consumeFromWhere = consumeFromWhere;
    }

    public String getTagExpression() {
        return tagExpression;
    }

    public void setTagExpression(String tagExpression) {
        this.tagExpression = tagExpression;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }
}
