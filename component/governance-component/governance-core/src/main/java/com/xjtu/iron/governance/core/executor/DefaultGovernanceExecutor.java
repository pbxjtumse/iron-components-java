package com.xjtu.iron.governance.core.executor;


import com.xjtu.iron.governance.api.context.GovernanceContext;
import com.xjtu.iron.governance.api.context.GovernanceContextHolder;
import com.xjtu.iron.governance.api.exception.FallbackFailedException;
import com.xjtu.iron.governance.api.fallback.FallbackHandler;
import com.xjtu.iron.governance.core.event.GovernanceEventBus;
import com.xjtu.iron.governance.core.exception.GovernanceExceptionMapperChain;
import com.xjtu.iron.governance.core.resolver.EffectiveGovernancePolicyResolver;
import com.xjtu.iron.governance.model.event.GovernanceEvent;
import com.xjtu.iron.governance.model.event.GovernanceEventType;
import com.xjtu.iron.governance.model.policy.GovernancePolicy;
import com.xjtu.iron.governance.spi.engine.GovernanceEngine;
import com.xjtu.iron.governance.spi.invocation.GovernanceInvocation;

public class DefaultGovernanceExecutor implements GovernanceExecutor {

    private final EffectiveGovernancePolicyResolver policyResolver;

    private final GovernanceEngine governanceEngine;

    private final GovernanceExceptionMapperChain exceptionMapperChain;

    private final GovernanceEventBus eventBus;

    public DefaultGovernanceExecutor(EffectiveGovernancePolicyResolver policyResolver,
                                     GovernanceEngine governanceEngine,
                                     GovernanceExceptionMapperChain exceptionMapperChain,
                                     GovernanceEventBus eventBus) {
        this.policyResolver = policyResolver;
        this.governanceEngine = governanceEngine;
        this.exceptionMapperChain = exceptionMapperChain;
        this.eventBus = eventBus;
    }

    @Override
    public <T> T execute(GovernanceContext context,
                         GovernanceInvocation<T> invocation) {
        return execute(context, invocation, null);
    }

    @Override
    public <T> T execute(GovernanceContext context,
                         GovernanceInvocation<T> invocation,
                         FallbackHandler<T> fallbackHandler) {
        GovernanceContextHolder.set(context);

        GovernancePolicy policy = policyResolver.resolve(context);

        eventBus.publish(GovernanceEvent.of(
                GovernanceEventType.CALL_STARTED,
                context.getResourceName()
        ));

        try {
            T result = governanceEngine.execute(context, policy, invocation);

            eventBus.publish(GovernanceEvent.of(
                    GovernanceEventType.CALL_SUCCEEDED,
                    context.getResourceName()
            ));

            return result;
        } catch (Throwable throwable) {
            RuntimeException mapped = exceptionMapperChain.map(context, throwable);

            eventBus.publish(GovernanceEvent.of(
                    GovernanceEventType.CALL_FAILED,
                    context.getResourceName()
            ));

            if (fallbackHandler != null) {
                return executeFallback(context, fallbackHandler, mapped);
            }

            throw mapped;
        } finally {
            GovernanceContextHolder.clear();
        }
    }

    private <T> T executeFallback(GovernanceContext context,
                                  FallbackHandler<T> fallbackHandler,
                                  RuntimeException mapped) {
        eventBus.publish(GovernanceEvent.of(
                GovernanceEventType.FALLBACK_TRIGGERED,
                context.getResourceName()
        ));

        try {
            return fallbackHandler.fallback(context, mapped);
        } catch (Throwable fallbackEx) {
            eventBus.publish(GovernanceEvent.of(
                    GovernanceEventType.FALLBACK_FAILED,
                    context.getResourceName()
            ));
            throw new FallbackFailedException(context.getResourceName(), fallbackEx);
        }
    }
}
