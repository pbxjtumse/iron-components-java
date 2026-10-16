package com.xjtu.iron.distributed.lock.core.registry;


import com.xjtu.iron.distributed.lock.spi.LockProvider;
import com.xjtu.iron.distributed.lock.spi.LockProviderRegistry;

import java.util.*;

/**
 * 默认 LockProvider 注册表。
 */
public final class DefaultLockProviderRegistry implements LockProviderRegistry {

    /** 默认 Provider 名称。 */
    private final String defaultProviderName;

    /** Provider 名称到 Provider 的映射。 */
    private final Map<String, LockProvider> providers;

    public DefaultLockProviderRegistry(String defaultProviderName, List<LockProvider> providers) {
        if (providers == null || providers.isEmpty()) {
            throw new IllegalArgumentException("providers must not be empty");
        }
        Map<String, LockProvider> map = new LinkedHashMap<>();
        for (LockProvider provider : providers) {
            Objects.requireNonNull(provider, "provider must not be null");
            String name = normalize(provider.providerName());
            if (map.putIfAbsent(name, provider) != null) {
                throw new IllegalArgumentException("duplicate lock provider: " + name);
            }
        }
        String normalizedDefaultName = defaultProviderName == null || defaultProviderName.trim().isEmpty()
                ? map.keySet().iterator().next()
                : normalize(defaultProviderName);
        if (!map.containsKey(normalizedDefaultName)) {
            throw new IllegalArgumentException("default provider not found: " + normalizedDefaultName);
        }
        this.defaultProviderName = normalizedDefaultName;
        this.providers = Collections.unmodifiableMap(map);
    }

    @Override
    public LockProvider getDefaultProvider() {
        return providers.get(defaultProviderName);
    }

    @Override
    public LockProvider getProvider(String providerName) {
        String name = providerName == null || providerName.trim().isEmpty() ? defaultProviderName : normalize(providerName);
        LockProvider provider = providers.get(name);
        if (provider == null) {
            throw new IllegalArgumentException("lock provider not found: " + name);
        }
        return provider;
    }

    @Override
    public boolean containsProvider(String providerName) {
        return providerName != null && providers.containsKey(normalize(providerName));
    }

    private static String normalize(String providerName) {
        if (providerName == null || providerName.trim().isEmpty()) {
            throw new IllegalArgumentException("providerName must not be blank");
        }
        return providerName.trim();
    }
}
