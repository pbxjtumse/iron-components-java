package com.xjtu.iron.message.spi;

import java.util.Set;
import java.util.concurrent.CompletionStage;
/**
 * 具体消息中间件适配层必须实现的 Provider SPI。
 * core稳定 ,Provider扩展简单 ,多MQ共存
 * <p>message-core 只面向这个接口编程，不直接依赖 Kafka、Pulsar、RocketMQ 客户端。
 * Provider 的职责是把统一的 {@code ProviderSendRequest} 转换成原生消息发送，
 * 并把原生发送结果、异常、订阅回调统一映射回 SPI 模型。</p>
 *
 * <p>Provider 不负责业务幂等、Outbox、通用重试策略选择，也不应该绕过 message-core 自己生成业务结果。
 * 它只负责“和某一个中间件打交道”。</p>
 */
public interface MessageProvider extends AutoCloseable {

    /**
     * 返回 Provider 稳定名称。
     *
     * @return 稳定名称，例如 kafka、rocketmq、pulsar
     */
    String name();

    /**
     * 返回 Provider 当前实现支持的公共能力。
     *
     * @return 不可变能力集合
     */
    Set<MessageCapability> capabilities();

    /**
     * 异步发送普通消息。
     *
     * @param request Provider 发送请求
     * @return Provider 标准结果
     */
    CompletionStage<ProviderSendResult> send(ProviderSendRequest request);

    /**
     * 创建并启动普通消息订阅。
     *
     * @param request Provider 订阅请求
     * @return Provider 订阅句柄
     */
    ProviderSubscription subscribe(ProviderSubscriptionRequest request);

    /**
     * 关闭 Provider 以及其持有的生产者和消费者资源。
     */
    @Override
    void close();
}
