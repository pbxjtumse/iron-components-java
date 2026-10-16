package com.xjtu.iron.governance.api.exception;

public class FallbackFailedException extends GovernanceException {

    public FallbackFailedException(String resourceName, Throwable cause) {
        super("Fallback execution failed: " + resourceName,
                resourceName,
                "GOV-500-002",
                false,
                cause);
    }
}
