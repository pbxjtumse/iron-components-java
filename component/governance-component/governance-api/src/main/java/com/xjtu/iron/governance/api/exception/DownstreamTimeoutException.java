package com.xjtu.iron.governance.api.exception;

public class DownstreamTimeoutException extends GovernanceException {

    public DownstreamTimeoutException(String resourceName, Throwable cause) {
        super("Downstream call timeout: " + resourceName,
                resourceName,
                "GOV-504-001",
                true,
                cause);
    }
}
