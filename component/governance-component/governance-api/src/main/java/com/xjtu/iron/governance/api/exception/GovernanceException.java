package com.xjtu.iron.governance.api.exception;

public class GovernanceException extends RuntimeException {

    private final String resourceName;

    private final String errorCode;

    private final boolean retryable;

    public GovernanceException(String message,
                               String resourceName,
                               String errorCode,
                               boolean retryable,
                               Throwable cause) {
        super(message, cause);
        this.resourceName = resourceName;
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public String getResourceName() {
        return resourceName;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
