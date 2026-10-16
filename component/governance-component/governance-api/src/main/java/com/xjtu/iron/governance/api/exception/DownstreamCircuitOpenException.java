package com.xjtu.iron.governance.api.exception;

public class DownstreamCircuitOpenException extends GovernanceException {

    public DownstreamCircuitOpenException(String resourceName, Throwable cause) {
        super("Circuit breaker is open: " + resourceName,
                resourceName,
                "GOV-503-001",
                false,
                cause);
    }
}
