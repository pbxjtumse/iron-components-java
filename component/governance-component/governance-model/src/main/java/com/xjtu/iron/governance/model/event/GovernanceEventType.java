package com.xjtu.iron.governance.model.event;


public enum GovernanceEventType {
    CALL_STARTED,
    CALL_SUCCEEDED,
    CALL_FAILED,
    CALL_REJECTED,
    CALL_TIMEOUT,
    CALL_RETRIED,
    FALLBACK_TRIGGERED,
    FALLBACK_FAILED,

    CIRCUIT_OPENED,
    CIRCUIT_HALF_OPEN,
    CIRCUIT_CLOSED,

    RULE_LOADED,
    RULE_CHANGED,
    RULE_LOAD_FAILED
}
