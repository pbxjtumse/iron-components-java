package com.xjtu.iron.message.api.consume.handler;

/**
 * 表示一个已经启动的消费订阅关闭句柄。
 */
@FunctionalInterface
/**
 * 消息订阅句柄，用来表示一次已经建立的消费订阅。
 *
 * <p>业务或 demo 可以保存该句柄，在应用关闭或测试结束时主动关闭订阅。
 * 它隐藏了 Kafka Consumer、Pulsar Consumer、RocketMQ PushConsumer 等不同客户端的生命周期差异。</p>
 */
public interface MessageSubscription extends AutoCloseable {

    /**
     * 停止消费并释放相关 Provider 资源。
     */
    @Override
    void close();
}
