package com.xjtu.iron.message.spi;

/**
 * 表示具体 Provider 已经启动的消费订阅。
 */
@FunctionalInterface
/**
 * Provider 层订阅句柄。
 *
 * <p>它屏蔽了不同中间件关闭订阅的细节，message-core 最终会把它适配为 API 层的 {@code MessageSubscription}。</p>
 */
public interface ProviderSubscription extends AutoCloseable {

    /**
     * 停止消费并释放 Provider 资源。
     */
    @Override
    void close();
}
