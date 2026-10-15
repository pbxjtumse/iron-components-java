package com.xjtu.iron.governance.model.engine;

public enum GovernanceEngineCapability {
    TIMEOUT,
    RETRY,
    CIRCUIT_BREAKER,
    BULKHEAD,
    RATE_LIMIT,
    HOT_KEY_RATE_LIMIT,
    SYSTEM_PROTECTION,
    INBOUND_PROTECTION
}
