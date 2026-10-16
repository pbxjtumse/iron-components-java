package com.xjtu.iron.governance.core.executor;


import com.xjtu.iron.governance.api.context.GovernanceContext;
import com.xjtu.iron.governance.api.fallback.FallbackHandler;
import com.xjtu.iron.governance.spi.invocation.GovernanceInvocation;

public interface GovernanceExecutor {

    <T> T execute(GovernanceContext context,
                  GovernanceInvocation<T> invocation);

    <T> T execute(GovernanceContext context,
                  GovernanceInvocation<T> invocation,
                  FallbackHandler<T> fallbackHandler);
}
