package com.xjtu.iron.message.core.provider;

import com.xjtu.iron.message.spi.MessageProvider;

import java.util.*;
/**
 * MessageProvider 注册表，统一管理 Kafka、Pulsar、RocketMQ 等 Provider 实例。
 *
 * <p>message-core 不直接依赖任何具体中间件客户端，只通过 {@code MessageProvider} SPI 发送和订阅。
 * 注册表负责按照 providerName 找到对应 Provider，并在组件关闭时统一释放 Provider 资源。</p>
 *
 * <p>如果业务指定的 providerName 没有注册，本类会直接抛出异常，后续由 MessageTemplate 映射为
 * {@code PROVIDER_NOT_FOUND}。这样可以尽早暴露配置错误，而不是运行时静默丢消息。</p>
 */
public final class MessageProviderRegistry implements AutoCloseable {

    /** 按稳定名称保存 Provider。 */
    private final Map<String, MessageProvider> providers;

    /**
     * 创建 Provider 注册表。
     *
     * @param providerCollection Provider 集合
     */
    public MessageProviderRegistry(Collection<? extends MessageProvider> providerCollection) {
        // Provider 集合不能为空。
        if (providerCollection == null || providerCollection.isEmpty()) {
            // 没有 Provider 时组件无法工作。
            throw new IllegalArgumentException("at least one message provider is required");
        }
        // 使用有序 Map 保持关闭顺序和诊断稳定。
        Map<String, MessageProvider> mutableProviders = new LinkedHashMap<>();
        // 逐个注册 Provider。
        for (MessageProvider provider : providerCollection) {
            // Provider 实例不能为空。
            if (provider == null) {
                // 启动阶段拒绝空 Provider。
                throw new IllegalArgumentException("message provider must not be null");
            }
            // Provider 名称不能为空。
            String providerName = provider.name();
            // 校验稳定名称。
            if (providerName == null || providerName.isBlank()) {
                // Provider 实现必须返回可用名称。
                throw new IllegalArgumentException("message provider name must not be blank");
            }
            // 去除名称首尾空白。
            String normalizedName = providerName.trim().toLowerCase(Locale.ROOT);
            // 同名 Provider 不允许覆盖。
            MessageProvider previous = mutableProviders.putIfAbsent(normalizedName, provider);
            // 发现重复时立即失败。
            if (previous != null) {
                // 输出冲突名称。
                throw new IllegalArgumentException("duplicate message provider: " + normalizedName);
            }
        }
        // 保存不可变 Provider Map。
        this.providers = Collections.unmodifiableMap(new LinkedHashMap<>(mutableProviders));
    }

    /**
     * 获取指定 Provider。
     *
     * @param providerName Provider 名称
     * @return Provider 实例
     */
    public MessageProvider getRequired(String providerName) {
        // Provider 名称不能为空。
        if (providerName == null || providerName.isBlank()) {
            // 空名称属于路由或调用错误。
            throw new IllegalArgumentException("providerName must not be blank");
        }
        // 查找 Provider。
        MessageProvider provider = providers.get(providerName.trim().toLowerCase(Locale.ROOT));
        // 不存在时抛出可诊断异常。
        if (provider == null) {
            // 附带当前已注册名称。
            throw new IllegalStateException(
                    "message provider not found: " + providerName
                            + ", registered=" + providers.keySet());
        }
        // 返回 Provider。
        return provider;
    }

    /**
     * 关闭全部 Provider。
     */
    @Override
    public void close() {
        // 逐个尝试关闭，单个失败不阻断其他资源释放。
        providers.values().forEach(provider -> {
            // 捕获关闭阶段运行时异常。
            try {
                // 释放生产者、消费者和网络资源。
                provider.close();
            } catch (RuntimeException ignored) {
                // 一期没有日志集成；二期通过生命周期事件记录关闭异常。
            }
        });
    }
}
