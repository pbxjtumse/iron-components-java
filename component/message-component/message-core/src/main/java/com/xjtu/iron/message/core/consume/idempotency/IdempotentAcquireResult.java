package com.xjtu.iron.message.core.consume.idempotency;

/**
 * acquire 调用结果。
 */
public final class IdempotentAcquireResult {
    private final IdempotentAcquireStatus status;
    private final String code;
    private final String description;

    public IdempotentAcquireResult(IdempotentAcquireStatus status, String code, String description) {
        this.status = status == null ? IdempotentAcquireStatus.STORAGE_ERROR : status;
        this.code = normalize(code);
        this.description = normalize(description);
    }

    public static IdempotentAcquireResult of(IdempotentAcquireStatus status) {
        return new IdempotentAcquireResult(status, null, null);
    }

    public IdempotentAcquireStatus status() { return status; }
    public String code() { return code; }
    public String description() { return description; }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
