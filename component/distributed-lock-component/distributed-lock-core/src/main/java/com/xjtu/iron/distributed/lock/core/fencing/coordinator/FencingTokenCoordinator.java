package com.xjtu.iron.distributed.lock.core.fencing.coordinator;

import com.xjtu.iron.distributed.lock.api.model.LockOptions;
import com.xjtu.iron.distributed.lock.core.fencing.registry.FencingTokenProviderRegistry;
import com.xjtu.iron.distributed.lock.spi.LockProvider;
import com.xjtu.iron.distributed.lock.spi.protocol.common.LockLease;

import java.util.Objects;

/**
 * fencing token 计划选择与外部发号协调器。
 *
 * <p>fencing 是锁组件的正确性增强：锁过期或旧 owner 恢复后，业务资源可以用单调递增 token 拒绝旧写。Coordinator 不负责真正锁操作，
 * 只根据 LockOptions 与 Provider 能力决定本次 token 来源。</p>
 *
 * <p>规则必须保守：如果业务要求 fencing，而当前 LockProvider 不支持 native fencing，又没有显式指定 external provider，则直接 fail-fast。
 * 不能因为系统中碰巧存在一个默认 JDBC provider 就偷偷替业务选择，因为 token 来源属于一致性边界，必须显式可见。</p>
 */
public final class FencingTokenCoordinator {

    /** 可按名称选择的外部 fencing token Provider 注册表。 */
    private final FencingTokenProviderRegistry registry;

    public FencingTokenCoordinator(FencingTokenProviderRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    public FencingTokenPlan plan(LockProvider lockProvider, LockOptions options) {
        Objects.requireNonNull(lockProvider, "lockProvider must not be null");
        Objects.requireNonNull(options, "options must not be null");
        if (!options.isFencingRequired()) {
            return FencingTokenPlan.none();
        }

        String explicitProviderName = trimToNull(options.getFencingTokenProviderName());
        if (explicitProviderName != null) {
            if (explicitProviderName.equals(lockProvider.providerName())) {
                if (!lockProvider.capabilities().isFencingTokenSupported()) {
                    throw new IllegalArgumentException("lock provider does not support native fencing token: " + lockProvider.providerName());
                }
                return FencingTokenPlan.nativeProvider();
            }
            return FencingTokenPlan.external(registry.getRequired(explicitProviderName));
        }

        if (lockProvider.capabilities().isFencingTokenSupported()) {
            return FencingTokenPlan.nativeProvider();
        }

        throw new IllegalArgumentException("fencing token is required, but lock provider has no native support. Please configure fencingTokenProviderName explicitly, for example mybatis-sequence: " + lockProvider.providerName());
    }

    /**
     * 调用 external fencing provider 发号。这里只做适配和异常捕获，真正发号语义由 Provider 实现，例如 JDBC sequence。
     */
    public com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenResponse issueExternal(FencingTokenPlan plan, LockLease lease, LockOptions options) {
        if (!plan.isExternal()) {
            throw new IllegalArgumentException("plan must be EXTERNAL");
        }
        com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenProvider provider = plan.externalProvider().orElseThrow();
        com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenRequest request = com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenRequest.builder().namespace(lease.getNamespace()).lockName(lease.getLockName())
                .ownerToken(lease.getOwnerToken()).options(options).build();
        if (!provider.supports(request)) {
            return com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenResponse.notSupported("fencing token provider does not support request: " + provider.providerName());
        }
        try {
            com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenResponse response = provider.nextToken(request);
            return response == null ? com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenResponse.failed(new IllegalStateException("fencing token provider returned null response: " + provider.providerName())) : response;
        } catch (Throwable error) {
            return com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenResponse.failed(error);
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
