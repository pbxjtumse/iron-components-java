package com.xjtu.iron.message.integration.pulsar.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Pulsar Provider 的 Spring Boot 配置属性。
 *
 * <p>该类只绑定 Pulsar 原生连接与消费参数，通用消息参数仍放在
 * {@code xjtu.iron.message} 对应的 MessageProperties 中。</p>
 */
@ConfigurationProperties(prefix = "xjtu.iron.message.pulsar")
/**
 * Pulsar Provider 的配置属性模型。
 *
 * <p>该类只描述 Pulsar 客户端参数，不包含消息组件的通用路由和可靠发送配置。
 * 通用配置仍然放在 {@code MessageProperties} 中。</p>
 */
public class PulsarMessageProperties {

    /**
     * 是否启用 Pulsar Provider 自动配置。
     */
    private boolean enabled = true;

    /**
     * Pulsar 服务地址。
     *
     * <p>集群外访问通常使用 pulsar://domain:6650。</p>
     */
    private String serviceUrl = "pulsar://localhost:6650";

    /**
     * Pulsar tenant。
     *
     * <p>当前基础 Provider 主要通过 routes.physical-name 指定完整 Topic；
     * 该字段保留给 Demo 默认 Topic 拼接和后续 Starter 便捷配置使用。</p>
     */
    private String tenant = "public";

    /**
     * Pulsar namespace。
     */
    private String namespace = "default";

    /**
     * Pulsar 客户端操作超时。
     */
    private Duration operationTimeout = Duration.ofSeconds(15);

    /**
     * negativeAcknowledge 后重新投递延迟。
     */
    private Duration negativeAckRedeliveryDelay = Duration.ofSeconds(2);

    /**
     * Consumer 接收队列大小。
     */
    private int receiverQueueSize = 1000;

    /**
     * Token 认证值。
     *
     * <p>未开启认证时保持为空；toString 和日志不应输出该字段。</p>
     */
    private String authenticationToken;

    /**
     * Demo 或后续便捷 Producer 配置。
     */
    private Producer producer = new Producer();

    /**
     * Demo 或后续便捷 Consumer 配置。
     */
    private Consumer consumer = new Consumer();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServiceUrl() {
        return serviceUrl;
    }

    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public Duration getOperationTimeout() {
        return operationTimeout;
    }

    public void setOperationTimeout(Duration operationTimeout) {
        this.operationTimeout = operationTimeout;
    }

    public Duration getNegativeAckRedeliveryDelay() {
        return negativeAckRedeliveryDelay;
    }

    public void setNegativeAckRedeliveryDelay(Duration negativeAckRedeliveryDelay) {
        this.negativeAckRedeliveryDelay = negativeAckRedeliveryDelay;
    }

    public int getReceiverQueueSize() {
        return receiverQueueSize;
    }

    public void setReceiverQueueSize(int receiverQueueSize) {
        this.receiverQueueSize = receiverQueueSize;
    }

    public String getAuthenticationToken() {
        return authenticationToken;
    }

    public void setAuthenticationToken(String authenticationToken) {
        this.authenticationToken = authenticationToken;
    }

    public Producer getProducer() {
        return producer;
    }

    public void setProducer(Producer producer) {
        this.producer = producer == null ? new Producer() : producer;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public void setConsumer(Consumer consumer) {
        this.consumer = consumer == null ? new Consumer() : consumer;
    }

    /**
     * Producer 便捷配置。
     *
     * <p>当前真正的发送目的地由通用 routes 决定，该配置主要服务 Demo 展示和后续扩展。</p>
     */
    public static class Producer {

        /** 物理 Topic。 */
        private String topic;

        /** Producer 名称。 */
        private String name;

        /** 发送超时秒数。 */
        private int sendTimeoutSeconds = 3;

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getSendTimeoutSeconds() {
            return sendTimeoutSeconds;
        }

        public void setSendTimeoutSeconds(int sendTimeoutSeconds) {
            this.sendTimeoutSeconds = sendTimeoutSeconds;
        }
    }

    /**
     * Consumer 便捷配置。
     *
     * <p>当前 Provider 订阅时仍以 ConsumerDefinition.consumerGroup 作为 Pulsar subscription。</p>
     */
    public static class Consumer {

        /** 物理 Topic。 */
        private String topic;

        /** Subscription 名称。 */
        private String subscriptionName;

        /** Subscription 类型字符串；一期基础 Provider 固定使用 Shared。 */
        private String subscriptionType = "Shared";

        /** Consumer 名称。 */
        private String consumerName;

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public String getSubscriptionName() {
            return subscriptionName;
        }

        public void setSubscriptionName(String subscriptionName) {
            this.subscriptionName = subscriptionName;
        }

        public String getSubscriptionType() {
            return subscriptionType;
        }

        public void setSubscriptionType(String subscriptionType) {
            this.subscriptionType = subscriptionType;
        }

        public String getConsumerName() {
            return consumerName;
        }

        public void setConsumerName(String consumerName) {
            this.consumerName = consumerName;
        }
    }
}
