package com.xjtu.iron.idempotent.core.observation;

import com.xjtu.iron.idempotent.api.execution.IdempotencyStage;
import com.xjtu.iron.idempotent.api.policy.IdempotencyMode;

import java.time.Instant;

/**
 * 幂等组件统一事件模型。
 *
 * <p>事件采用“一个事件类 + EventType”的方式，避免为每个生命周期节点创建独立事件类。
 * 事件只用于观测，不应反向改变幂等状态机。</p>
 */
public final class IdempotencyEvent {

    private final IdempotencyEventType type;
    private final IdempotencyStage stage;
    private final IdempotencyMode mode;
    private final String repository;
    private final Instant occurredAt;
    private final Throwable error;

    public IdempotencyEvent(
            IdempotencyEventType type,
            IdempotencyStage stage,
            IdempotencyMode mode,
            String repository,
            Instant occurredAt,
            Throwable error) {
        this.type = type;
        this.stage = stage;
        this.mode = mode;
        this.repository = repository;
        this.occurredAt = occurredAt;
        this.error = error;
    }

    public IdempotencyEventType getType() { return type; }
    public IdempotencyStage getStage() { return stage; }
    public IdempotencyMode getMode() { return mode; }
    public String getRepository() { return repository; }
    public Instant getOccurredAt() { return occurredAt; }
    public Throwable getError() { return error; }
}
