package com.xjtu.iron.governance.spi.engine;


import com.xjtu.iron.governance.api.context.GovernanceContext;
import com.xjtu.iron.governance.model.engine.GovernanceEngineCapability;
import com.xjtu.iron.governance.model.engine.GovernanceEngineType;
import com.xjtu.iron.governance.model.policy.GovernancePolicy;
import com.xjtu.iron.governance.spi.invocation.GovernanceInvocation;

import java.util.Set;

public interface GovernanceEngine {

    GovernanceEngineType engineType();

    Set<GovernanceEngineCapability> capabilities();

    <T> T execute(GovernanceContext context, GovernancePolicy policy, GovernanceInvocation<T> invocation) throws Throwable;

}
