package com.xjtu.iron.idempotent.core.state;

import com.xjtu.iron.idempotent.api.execution.IdempotencyResultStatus;

import java.util.Objects;

/**
 * State Machine 对 Repository 判定状态的纯决策结果。
 *
 * <p>注意：State Machine 本身不做数据库查询和更新。
 * 原子状态转换仍由 Repository CAS/Lua/行锁完成，避免出现“Java 先查再改”的竞态。</p>
 */
public final class IdempotencyStateDecision {

    private final IdempotencyStateAction action;
    private final IdempotencyResultStatus resultStatus;

    private IdempotencyStateDecision(IdempotencyStateAction action, IdempotencyResultStatus resultStatus) {
        this.action = Objects.requireNonNull(action, "action must not be null");
        this.resultStatus = resultStatus;
    }

    public static IdempotencyStateDecision execute() {
        return new IdempotencyStateDecision(IdempotencyStateAction.EXECUTE, null);
    }

    public static IdempotencyStateDecision replay() {
        return new IdempotencyStateDecision(IdempotencyStateAction.REPLAY, null);
    }

    public static IdempotencyStateDecision returning(IdempotencyResultStatus status) {
        return new IdempotencyStateDecision(IdempotencyStateAction.RETURN, status);
    }

    public IdempotencyStateAction action() { return action; }
    public IdempotencyResultStatus resultStatus() { return resultStatus; }
}
