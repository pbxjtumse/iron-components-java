package com.xjtu.iron.governance.api.exception;

public class DownstreamCallException extends GovernanceException {

    public DownstreamCallException(String resourceName, Throwable cause) {
        super("Downstream call failed: " + resourceName,
                resourceName,
                "GOV-500-001",
                true,
                cause);
    }
}
