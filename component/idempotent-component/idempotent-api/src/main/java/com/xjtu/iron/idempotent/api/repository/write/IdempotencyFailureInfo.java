package com.xjtu.iron.idempotent.api.repository.write;

import java.time.Instant;

/**
 * 业务 callback 失败后写入幂等记录的标准失败描述。
 *
 * <p>{@code retryable} 只表达“该失败从业务语义上允许恢复”，
 * 不代表普通 {@code execute()} 会立即重试。真正的再次执行必须由显式
 * {@code recover()} 路径触发。</p>
 */
public final class IdempotencyFailureInfo {

    /** 稳定失败码，建议用于查询、告警和恢复策略判断。 */
    private final String code;

    /** 面向排障的失败描述，不建议承载业务控制逻辑。 */
    private final String message;

    /** 是否允许 Reliable Task 在后续显式恢复。 */
    private final boolean retryable;

    /** 失败发生时间。 */
    private final Instant occurredAt;

    public IdempotencyFailureInfo(String code, String message, boolean retryable, Instant occurredAt) {
        this.code = code;
        this.message = message;
        this.retryable = retryable;
        this.occurredAt = occurredAt;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
    public boolean isRetryable() { return retryable; }
    public Instant getOccurredAt() { return occurredAt; }
}
