package com.xjtu.iron.message.integration.pulsar.config;

import java.time.Duration;
import java.util.Objects;

/**
 * 表示 Pulsar 稳定 Java Client 的一期基础配置。
 *
 * <p>{@code serviceUrl}：Pulsar 服务地址</p>
 * <p>{@code operationTimeout}：客户端操作超时</p>
 * <p>{@code negativeAckRedeliveryDelay}：Negative ACK 后重新投递延迟</p>
 * <p>{@code receiverQueueSize}：Consumer 接收队列大小</p>
 * <p>{@code authenticationToken}：可选 Token 认证值</p>
 */
public final class PulsarMessageProviderConfig {
    /** Pulsar 服务地址。 */
    private final String serviceUrl;

    /** 客户端操作超时。 */
    private final Duration operationTimeout;

    /** Negative ACK 后重新投递延迟。 */
    private final Duration negativeAckRedeliveryDelay;

    /** Consumer 接收队列大小。 */
    private final int receiverQueueSize;

    /** 可选 Token 认证值。 */
    private final String authenticationToken;


    /**
     * 校验并标准化 Pulsar 配置。
     */
    public PulsarMessageProviderConfig(
        String serviceUrl,
        Duration operationTimeout,
        Duration negativeAckRedeliveryDelay,
        int receiverQueueSize,
        String authenticationToken) {
        // serviceUrl 必须存在。
        if (serviceUrl == null || serviceUrl.isBlank()) {
            // 没有服务地址无法创建客户端。
            throw new IllegalArgumentException("serviceUrl must not be blank");
        }
        // 去除地址首尾空白。
        serviceUrl = serviceUrl.trim();
        // operationTimeout 必须为正数。
        if (operationTimeout == null
                || operationTimeout.isZero()
                || operationTimeout.isNegative()) {
            // 非正超时无意义。
            throw new IllegalArgumentException("operationTimeout must be positive");
        }
        // Negative ACK 延迟允许为零但不能为负数。
        if (negativeAckRedeliveryDelay == null
                || negativeAckRedeliveryDelay.isNegative()) {
            // null 或负数都属于非法配置。
            throw new IllegalArgumentException(
                    "negativeAckRedeliveryDelay must not be negative");
        }
        // 接收队列不能为负数，零表示禁用预取。
        if (receiverQueueSize < 0) {
            // 拒绝非法队列大小。
            throw new IllegalArgumentException("receiverQueueSize must not be negative");
        }
        // 空白 Token 统一转换为 null。
        authenticationToken = authenticationToken == null
                || authenticationToken.isBlank()
                ? null
                : authenticationToken.trim();
    
        // 保存完成校验和标准化后的 serviceUrl。
        this.serviceUrl = serviceUrl;
        // 保存完成校验和标准化后的 operationTimeout。
        this.operationTimeout = operationTimeout;
        // 保存完成校验和标准化后的 negativeAckRedeliveryDelay。
        this.negativeAckRedeliveryDelay = negativeAckRedeliveryDelay;
        // 保存完成校验和标准化后的 receiverQueueSize。
        this.receiverQueueSize = receiverQueueSize;
        // 保存完成校验和标准化后的 authenticationToken。
        this.authenticationToken = authenticationToken;
    }

    /**
     * 创建无鉴权默认配置。
     *
     * @param serviceUrl Pulsar 服务地址
     * @return 默认配置
     */
    public static PulsarMessageProviderConfig defaults(String serviceUrl) {
        // 默认三秒操作超时、一秒负确认重投延迟和 1000 条接收队列。
        return new PulsarMessageProviderConfig(
                serviceUrl,
                Duration.ofSeconds(3),
                Duration.ofSeconds(1),
                1000,
                null);
    }

    /**
     * 创建适合通过公网或跨网络访问 Pulsar 的调试配置。
     *
     * <p>公网链路通常比集群内链路具有更高的 DNS、连接、Topic Lookup 和 Broker 确认延迟，
     * 因此这里使用十五秒操作超时，避免首次建立连接时被三秒默认值过早判定失败。</p>
     *
     * <p>该方法只调整调试阶段的客户端等待时间，不改变消息确认、Negative ACK 和
     * Shared Subscription 的基础语义。</p>
     *
     * @param serviceUrl Pulsar 二进制服务地址，例如 pulsar://pulsar.example.com:6650
     * @param authenticationToken 可选 Token；未开启认证时传 null
     * @return 公网调试配置
     */
    public static PulsarMessageProviderConfig externalDebug(
            String serviceUrl,
            String authenticationToken) {
        // 公网调试使用十五秒操作超时、两秒负确认重投延迟和 1000 条接收队列。
        return new PulsarMessageProviderConfig(
                serviceUrl,
                Duration.ofSeconds(15),
                Duration.ofSeconds(2),
                1000,
                authenticationToken);
    }
    /**
     * 返回Pulsar 服务地址。
     *
     * @return Pulsar 服务地址
     */
    public String serviceUrl() {
        // 返回不可变字段。
        return serviceUrl;
    }

    /**
     * 返回客户端操作超时。
     *
     * @return 客户端操作超时
     */
    public Duration operationTimeout() {
        // 返回不可变字段。
        return operationTimeout;
    }

    /**
     * 返回Negative ACK 后重新投递延迟。
     *
     * @return Negative ACK 后重新投递延迟
     */
    public Duration negativeAckRedeliveryDelay() {
        // 返回不可变字段。
        return negativeAckRedeliveryDelay;
    }

    /**
     * 返回Consumer 接收队列大小。
     *
     * @return Consumer 接收队列大小
     */
    public int receiverQueueSize() {
        // 返回不可变字段。
        return receiverQueueSize;
    }

    /**
     * 返回可选 Token 认证值。
     *
     * @return 可选 Token 认证值
     */
    public String authenticationToken() {
        // 返回不可变字段。
        return authenticationToken;
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
        PulsarMessageProviderConfig other = (PulsarMessageProviderConfig) object;
        return Objects.equals(serviceUrl, other.serviceUrl)
                && Objects.equals(operationTimeout, other.operationTimeout)
                && Objects.equals(negativeAckRedeliveryDelay, other.negativeAckRedeliveryDelay)
                && receiverQueueSize == other.receiverQueueSize
                && Objects.equals(authenticationToken, other.authenticationToken);
    }

    /**
     * 根据全部字段计算哈希值。
     *
     * @return 哈希值
     */
    @Override
    public int hashCode() {
        // 使用与 equals 相同的字段计算哈希值。
        return Objects.hash(serviceUrl, operationTimeout, negativeAckRedeliveryDelay, receiverQueueSize, authenticationToken);
    }

    /**
     * 返回便于诊断的字段摘要。
     *
     * @return 字符串摘要
     */
    @Override
    public String toString() {
        // Token 只输出是否配置，禁止把认证值写入日志。
        return "PulsarMessageProviderConfig{" +
                "serviceUrl=" + serviceUrl +
                ", operationTimeout=" + operationTimeout +
                ", negativeAckRedeliveryDelay=" + negativeAckRedeliveryDelay +
                ", receiverQueueSize=" + receiverQueueSize +
                ", authenticationConfigured=" + (authenticationToken != null) +
                '}';
    }

}
