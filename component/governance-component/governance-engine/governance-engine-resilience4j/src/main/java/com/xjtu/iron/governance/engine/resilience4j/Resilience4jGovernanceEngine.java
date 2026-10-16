package com.xjtu.iron.governance.engine.resilience4j;

import com.xjtu.iron.governance.api.context.GovernanceContext;
import com.xjtu.iron.governance.core.timeout.CallTimeoutExecutor;
import com.xjtu.iron.governance.model.engine.GovernanceEngineCapability;
import com.xjtu.iron.governance.model.engine.GovernanceEngineType;
import com.xjtu.iron.governance.model.policy.GovernancePolicy;
import com.xjtu.iron.governance.spi.engine.GovernanceEngine;
import com.xjtu.iron.governance.spi.invocation.GovernanceInvocation;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

public class Resilience4jGovernanceEngine implements GovernanceEngine {

    private final ConcurrentMap<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Retry> retries = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Bulkhead> bulkheads = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

    private final CallTimeoutExecutor timeoutExecutor;

    public Resilience4jGovernanceEngine(CallTimeoutExecutor timeoutExecutor) {
        this.timeoutExecutor = timeoutExecutor;
    }

    @Override
    public GovernanceEngineType engineType() {
        return GovernanceEngineType.RESILIENCE4J;
    }

    @Override
    public Set<GovernanceEngineCapability> capabilities() {
        return Set.of(
                GovernanceEngineCapability.TIMEOUT,
                GovernanceEngineCapability.RETRY,
                GovernanceEngineCapability.CIRCUIT_BREAKER,
                GovernanceEngineCapability.BULKHEAD,
                GovernanceEngineCapability.RATE_LIMIT
        );
    }

    @Override
    public <T> T execute(GovernanceContext context,
                         GovernancePolicy policy,
                         GovernanceInvocation<T> invocation) throws Throwable {

        Supplier<T> supplier = () -> {
            try {
                return invocation.invoke(context);
            } catch (RuntimeException runtimeException) {
                throw runtimeException;
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        };

        String resourceName = context.getResourceName();

        /*
         * 最终执行顺序：
         *
         * Timeout 外层控制总等待时间
         * RateLimiter 控制一次业务请求准入
         * Bulkhead 控制并发隔离
         * CircuitBreaker 控制熔断
         * Retry 控制真实调用重试
         *
         * 这样可以避免：
         * 1. 熔断打开后还继续重试
         * 2. 被限流后还继续重试
         * 3. 一个下游故障占满全部调用线程
         */

        if (policy.getRetry() != null && policy.getRetry().isEnabled()) {
            Retry retry = getOrCreateRetry(resourceName, policy);
            supplier = Retry.decorateSupplier(retry, supplier);
        }

        if (policy.getCircuitBreaker() != null && policy.getCircuitBreaker().isEnabled()) {
            CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(resourceName, policy);
            supplier = CircuitBreaker.decorateSupplier(circuitBreaker, supplier);
        }

        if (policy.getBulkhead() != null && policy.getBulkhead().isEnabled()) {
            Bulkhead bulkhead = getOrCreateBulkhead(resourceName, policy);
            supplier = Bulkhead.decorateSupplier(bulkhead, supplier);
        }

        if (policy.getRateLimit() != null && policy.getRateLimit().isEnabled()) {
            RateLimiter rateLimiter = getOrCreateRateLimiter(resourceName, policy);
            supplier = RateLimiter.decorateSupplier(rateLimiter, supplier);
        }

        if (policy.getTimeout() != null && policy.getTimeout().isEnabled()) {
            return timeoutExecutor.execute(supplier, policy.getTimeout().getTimeout());
        }

        return supplier.get();
    }

    private Retry getOrCreateRetry(String resourceName, GovernancePolicy policy) {
        return retries.computeIfAbsent(resourceName, key -> {
            var p = policy.getRetry();

            RetryConfig config = RetryConfig.custom()
                    .maxAttempts(Math.max(1, p.getMaxAttempts()))
                    .waitDuration(p.getWaitDuration())
                    .build();

            return Retry.of(key, config);
        });
    }

    private CircuitBreaker getOrCreateCircuitBreaker(String resourceName, GovernancePolicy policy) {
        return circuitBreakers.computeIfAbsent(resourceName, key -> {
            var p = policy.getCircuitBreaker();

            CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                    .failureRateThreshold(p.getFailureRateThreshold())
                    .slowCallRateThreshold(p.getSlowCallRateThreshold())
                    .slowCallDurationThreshold(p.getSlowCallDurationThreshold())
                    .slidingWindowSize(p.getSlidingWindowSize())
                    .minimumNumberOfCalls(p.getMinimumNumberOfCalls())
                    .waitDurationInOpenState(p.getWaitDurationInOpenState())
                    .permittedNumberOfCallsInHalfOpenState(p.getPermittedNumberOfCallsInHalfOpenState())
                    .build();

            return CircuitBreaker.of(key, config);
        });
    }

    private Bulkhead getOrCreateBulkhead(String resourceName, GovernancePolicy policy) {
        return bulkheads.computeIfAbsent(resourceName, key -> {
            var p = policy.getBulkhead();

            BulkheadConfig config = BulkheadConfig.custom()
                    .maxConcurrentCalls(p.getMaxConcurrentCalls())
                    .maxWaitDuration(p.getMaxWaitDuration())
                    .build();

            return Bulkhead.of(key, config);
        });
    }

    private RateLimiter getOrCreateRateLimiter(String resourceName, GovernancePolicy policy) {
        return rateLimiters.computeIfAbsent(resourceName, key -> {
            var p = policy.getRateLimit();

            RateLimiterConfig config = RateLimiterConfig.custom()
                    .limitForPeriod(p.getLimitForPeriod())
                    .limitRefreshPeriod(p.getLimitRefreshPeriod())
                    .timeoutDuration(p.getTimeoutDuration())
                    .build();

            return RateLimiter.of(key, config);
        });
    }
}