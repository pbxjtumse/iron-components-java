package com.xjtu.iron.message.core.send;


import com.xjtu.iron.message.api.model.MessageDestination;
import com.xjtu.iron.message.api.model.MessageEnvelope;
import com.xjtu.iron.message.spi.MessageProvider;
import com.xjtu.iron.message.spi.ProviderDestination;
import com.xjtu.iron.message.spi.ProviderSendRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
/**
 * 一次发送在进入执行器之前的不可变快照。
 *
 * <p>{@code MessageTemplate} 完成校验、消息补齐、目的地解析、Provider 选择、wire codec 编码之后，
 * 会把所有结果封装到这个对象里。后续不管走 {@code DirectMessageSender} 还是
 * {@code DefaultReliableMessageSender}，都只依赖这个快照，不再重复做前置准备。</p>
 *
 * <p>这个设计的好处是把“准备发送”和“执行发送”拆开：准备阶段的失败可以直接映射为 REJECTED 或 FAILED，
 * 执行阶段的失败则由 direct/reliable sender 负责映射为 CONFIRMED、FAILED、UNKNOWN 或 RETRY_EXHAUSTED。</p>
 */
public final class PreparedMessageSend {

    /** 业务调用时指定的逻辑目的地。 */
    private final MessageDestination destination;

    /** 已经经过 enrich 的消息信封。 */
    private final MessageEnvelope<?> message;

    /** 已解析出的 Provider 物理目的地。 */
    private final ProviderDestination providerDestination;

    /** 实际执行发送的 Provider。 */
    private final MessageProvider provider;

    /** 交给 Provider 的线级发送请求。 */
    private final ProviderSendRequest request;

    /** 单次发送等待确认的最大时间。 */
    private final Duration confirmTimeout;

    /** 整个发送调用开始时间。 */
    private final Instant startedAt;

    public PreparedMessageSend(
            MessageDestination destination,
            MessageEnvelope<?> message,
            ProviderDestination providerDestination,
            MessageProvider provider,
            ProviderSendRequest request,
            Duration confirmTimeout,
            Instant startedAt) {
        this.destination = Objects.requireNonNull(destination, "destination must not be null");
        this.message = Objects.requireNonNull(message, "message must not be null");
        this.providerDestination = Objects.requireNonNull(providerDestination, "providerDestination must not be null");
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.request = Objects.requireNonNull(request, "request must not be null");
        this.confirmTimeout = Objects.requireNonNull(confirmTimeout, "confirmTimeout must not be null");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
    }

    public MessageDestination destination() {
        return destination;
    }

    public MessageEnvelope<?> message() {
        return message;
    }

    public ProviderDestination providerDestination() {
        return providerDestination;
    }

    public MessageProvider provider() {
        return provider;
    }

    public ProviderSendRequest request() {
        return request;
    }

    public Duration confirmTimeout() {
        return confirmTimeout;
    }

    public Instant startedAt() {
        return startedAt;
    }
}
