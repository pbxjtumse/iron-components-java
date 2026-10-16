package com.xjtu.iron.distributed.lock.core.acquire;

import com.xjtu.iron.distributed.lock.api.LockWaitStrategy;
import com.xjtu.iron.distributed.lock.api.exception.InvalidLockOptionsException;
import com.xjtu.iron.distributed.lock.api.model.LockHandle;
import com.xjtu.iron.distributed.lock.api.model.LockOptions;
import com.xjtu.iron.distributed.lock.api.model.LockResult;
import com.xjtu.iron.distributed.lock.api.status.LockStage;
import com.xjtu.iron.distributed.lock.api.status.LockStatus;
import com.xjtu.iron.distributed.lock.core.fencing.coordinator.FencingTokenCoordinator;
import com.xjtu.iron.distributed.lock.core.fencing.coordinator.FencingTokenPlan;
import com.xjtu.iron.distributed.lock.core.observability.LockEventFactory;
import com.xjtu.iron.distributed.lock.core.observability.LockEventPublisher;
import com.xjtu.iron.distributed.lock.core.observability.LockEventType;
import com.xjtu.iron.distributed.lock.core.support.LockNameValidator;
import com.xjtu.iron.distributed.lock.core.support.OwnerTokenGenerator;
import com.xjtu.iron.distributed.lock.core.wait.LockWaitContext;
import com.xjtu.iron.distributed.lock.core.wait.LockWaiter;
import com.xjtu.iron.distributed.lock.core.wait.LockWaiterFactory;
import com.xjtu.iron.distributed.lock.spi.LockProvider;
import com.xjtu.iron.distributed.lock.spi.LockProviderRegistry;
import com.xjtu.iron.distributed.lock.spi.protocol.acquire.LockAcquireRequest;
import com.xjtu.iron.distributed.lock.spi.protocol.acquire.LockAcquireResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 加锁主流程服务。
 *
 * <p>本类对应一次 {@code tryLock} 的完整业务用例，不是简单工具类。它负责把“用户输入”转换为“Provider 可执行的加锁请求”，再把
 * Provider 的有限状态响应交给 {@link LockAcquireOutcomeHandlerRegistry} 解释成公开 {@link LockResult}。</p>
 *
 * <p>这里保留 request/context 的组装逻辑，是一种有意识的平衡：创建 {@code LockAcquireRequestFactory}、
 * {@code LockAcquireOutcomeContextFactory} 会让类更多，但不会增加新的变化点。当前类已经是 acquire 用例的边界，把短小组装放在这里更容易读。</p>
 */
public final class LockAcquisitionService {

    private final LockProviderRegistry providerRegistry;
    private final OwnerTokenGenerator ownerTokenGenerator;
    private final LockWaiterFactory waiterFactory;
    private final LockEventPublisher eventPublisher;
    private final LockEventFactory eventFactory;
    private final LockNameValidator lockNameValidator;
    private final LockOptions defaultOptions;
    private final Clock clock;
    private final FencingTokenCoordinator fencingTokenCoordinator;
    private final LockAcquireOutcomeHandlerRegistry outcomeHandlerRegistry;

    public LockAcquisitionService(LockProviderRegistry providerRegistry, OwnerTokenGenerator ownerTokenGenerator, LockWaiterFactory waiterFactory,
            LockEventPublisher eventPublisher, LockEventFactory eventFactory, LockNameValidator lockNameValidator, LockOptions defaultOptions,
            Clock clock, FencingTokenCoordinator fencingTokenCoordinator, LockAcquireOutcomeHandlerRegistry outcomeHandlerRegistry) {
        this.providerRegistry = Objects.requireNonNull(providerRegistry, "providerRegistry must not be null");
        this.ownerTokenGenerator = Objects.requireNonNull(ownerTokenGenerator, "ownerTokenGenerator must not be null");
        this.waiterFactory = Objects.requireNonNull(waiterFactory, "waiterFactory must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.eventFactory = Objects.requireNonNull(eventFactory, "eventFactory must not be null");
        this.lockNameValidator = Objects.requireNonNull(lockNameValidator, "lockNameValidator must not be null");
        this.defaultOptions = defaultOptions == null ? LockOptions.defaults() : defaultOptions;
        this.defaultOptions.validate();
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.fencingTokenCoordinator = Objects.requireNonNull(fencingTokenCoordinator, "fencingTokenCoordinator must not be null");
        this.outcomeHandlerRegistry = Objects.requireNonNull(outcomeHandlerRegistry, "outcomeHandlerRegistry must not be null");
    }

    /** 对外 tryLock 入口，只返回公开结果，不暴露内部 AcquireAttempt。 */
    public LockResult<LockHandle> tryLock(String lockName, LockOptions options) {
        return acquire(lockName, options).result();
    }

    /**
     * 执行一次加锁，并保留本次解析后的 {@link LockOptions}。
     *
     * <p>{@code execute} 需要知道 acquire 时真正使用的默认值、providerName、leaseTime、autoRenew 等配置。把这些信息放进
     * {@link AcquireAttempt}，可以避免 execute 再对原始 options 做一次推导，减少“tryLock 用一套 options、execute 又推导出另一套”的隐患。</p>
     */
    public AcquireAttempt acquire(String lockName, LockOptions options) {
        Instant operationStart = Instant.now(clock);
        try {
            LockOptions actualOptions = resolveAndValidate(lockName, options);
            LockProvider provider = providerRegistry.getProvider(actualOptions.getProviderName());

            // fencing 计划必须在构造 LockAcquireRequest 之前确定，因为 native fencing 会影响 Provider acquire 行为。
            FencingTokenPlan fencingPlan = fencingTokenCoordinator.plan(provider, actualOptions);
            validateProviderCapabilities(provider, actualOptions);

            String ownerToken = ownerTokenGenerator.generate(actualOptions.getNamespace(), lockName);
            LockAcquireRequest request = LockAcquireRequest.builder().lockName(lockName).ownerToken(ownerToken).options(actualOptions)
                    .nativeFencingRequired(fencingPlan.isNative()).build();

            eventPublisher.publish(eventFactory.fromAcquireRequest(provider, request, LockEventType.ACQUIRE_ATTEMPT, LockStage.ACQUIRE, null, null));

            // 等待策略也是扩展点：NO_WAIT/BACKOFF 由 Core 控制，PROVIDER_NATIVE 把等待交给 Redisson/ZK/Etcd 等 Provider。
            LockWaiter waiter = waiterFactory.getWaiter(actualOptions.getWaitStrategy());
            Instant acquireStart = Instant.now(clock);
            LockAcquireResponse response = waiter.waitForLock(new LockWaitContext(request, provider, clock));
            Duration waitDuration = Duration.between(acquireStart, Instant.now(clock));

            LockAcquireOutcomeContext outcomeContext = LockAcquireOutcomeContext.builder().lockName(lockName).provider(provider).options(actualOptions)
                    .request(request).response(response).fencingPlan(fencingPlan).waitDuration(waitDuration).build();
            return AcquireAttempt.completed(outcomeHandlerRegistry.handle(outcomeContext), actualOptions);
        } catch (IllegalArgumentException | InvalidLockOptionsException error) {
            return AcquireAttempt.invalid(invalidOptionsResult(lockName, error, operationStart));
        }
    }

    private LockOptions resolveAndValidate(String lockName, LockOptions options) {
        lockNameValidator.validate(lockName);
        LockOptions actualOptions = options == null ? defaultOptions : options;
        actualOptions.validate();
        return actualOptions;
    }

    private void validateProviderCapabilities(LockProvider provider, LockOptions options) {
        if (options.isAutoRenew() && !provider.capabilities().isAutoRenewSupported()) {
            throw new IllegalArgumentException("provider does not support auto renew: " + provider.providerName());
        }
        if (options.getWaitStrategy() == LockWaitStrategy.PROVIDER_NATIVE && !provider.capabilities().isNativeWaitSupported()) {
            throw new IllegalArgumentException("provider does not support provider-native waiting: " + provider.providerName());
        }
        // 通用能力校验只能覆盖 cross-provider 规则；Provider 自身约束，例如 Redisson watchdogTimeout 与 leaseTime 的关系，由 Provider 自己校验。
        provider.validateOptions(options);
    }

    private LockResult<LockHandle> invalidOptionsResult(String lockName, RuntimeException error, Instant operationStart) {
        return LockResult.<LockHandle>builder().status(LockStatus.INVALID_OPTIONS).stage(LockStage.VALIDATE).acquired(false).error(error)
                .lockName(lockName).waitDuration(Duration.between(operationStart, Instant.now(clock))).build();
    }

    /**
     * acquire 内部返回对象。它不是公开 API，只服务 execute 流程复用“本次已解析 options”。
     */
    public static final class AcquireAttempt {
        private final LockResult<LockHandle> result;
        private final LockOptions options;

        private AcquireAttempt(LockResult<LockHandle> result, LockOptions options) {
            this.result = Objects.requireNonNull(result, "result must not be null");
            this.options = options;
        }

        public static AcquireAttempt completed(LockResult<LockHandle> result, LockOptions options) {
            return new AcquireAttempt(result, Objects.requireNonNull(options, "options must not be null"));
        }

        public static AcquireAttempt invalid(LockResult<LockHandle> result) {
            return new AcquireAttempt(result, null);
        }

        public LockResult<LockHandle> result() {
            return result;
        }

        public LockOptions requireOptions() {
            if (options == null) {
                throw new IllegalStateException("acquire attempt has no resolved options");
            }
            return options;
        }
    }
}
