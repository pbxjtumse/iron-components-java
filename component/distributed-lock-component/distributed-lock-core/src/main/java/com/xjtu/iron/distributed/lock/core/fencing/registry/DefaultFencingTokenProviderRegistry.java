package com.xjtu.iron.distributed.lock.core.fencing.registry;

import com.xjtu.iron.distributed.lock.core.fencing.coordinator.FencingTokenCoordinator;

import java.util.*;

/**
 * 默认 fencing token Provider 注册表。
 *
 * <p>只维护 providerName -> Provider 的稳定映射。这里刻意不设计 defaultProvider：
 * external fencing 的选择必须由 {@link FencingTokenCoordinator} 根据显式配置完成，
 * 避免容器中 Bean 数量变化悄悄改变一致性策略。</p>
 */
public final class DefaultFencingTokenProviderRegistry implements FencingTokenProviderRegistry {

    private final Map<String, com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenProvider> providers;

    public DefaultFencingTokenProviderRegistry(Collection<? extends com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenProvider> providers) {
        Map<String, com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenProvider> mapped = new LinkedHashMap<>();
        if (providers != null) {
            for (com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenProvider provider : providers) {
                Objects.requireNonNull(provider, "fencing token provider must not be null");
                String name = normalize(provider.providerName());
                com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenProvider previous = mapped.putIfAbsent(name, provider);
                if (previous != null) {
                    throw new IllegalArgumentException("duplicate fencing token provider: " + name);
                }
            }
        }
        this.providers = Collections.unmodifiableMap(mapped);
    }

    @Override
    public Optional<com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenProvider> findProvider(String providerName) {
        String normalized = normalizeNullable(providerName);
        if (normalized == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(providers.get(normalized));
    }

    @Override
    public Set<String> providerNames() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(providers.keySet()));
    }

    private static String normalize(String value) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new IllegalArgumentException("providerName must not be blank");
        }
        return normalized;
    }

    private static String normalizeNullable(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
