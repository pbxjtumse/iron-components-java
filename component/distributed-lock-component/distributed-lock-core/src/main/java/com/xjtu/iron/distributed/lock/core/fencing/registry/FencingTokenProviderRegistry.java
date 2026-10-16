package com.xjtu.iron.distributed.lock.core.fencing.registry;

import com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenProvider;

import java.util.Optional;
import java.util.Set;

/**
 * 独立 fencing token Provider 注册表。
 *
 * <p>注册表只负责“按名称查找 Provider”，不再提供默认 external Provider 推导。
 * fencing 属于一致性策略：当 LockProvider 不支持 native fencing 时，调用方必须通过
 * {@code LockOptions.fencingTokenProviderName} 明确指定 external Provider，不能根据 Bean 数量猜测。</p>
 */
public interface FencingTokenProviderRegistry {

    Optional<FencingTokenProvider> findProvider(String providerName);

    default FencingTokenProvider getRequired(String providerName) {
        return findProvider(providerName).orElseThrow(() -> new IllegalArgumentException(
                        "fencing token provider not found: " + providerName));
    }

    Set<String> providerNames();
}
