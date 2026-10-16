package com.xjtu.iron.governance.api.exception;

public class DownstreamBulkheadFullException extends GovernanceException {

    public DownstreamBulkheadFullException(String resourceName, Throwable cause) {
        super("Bulkhead is full: " + resourceName,
                resourceName,
                "GOV-503-002",
                false,
                cause);
    }
}
