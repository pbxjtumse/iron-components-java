package com.xjtu.iron.concurrency.api.error;


import com.xjtu.iron.concurrency.api.enums.error.AsyncRecoveryAction;

/**
 * 错误恢复建议。
 *
 * <p>
 * 用于给治理组件、补偿系统、业务监听器提供结构化建议。
 * 并行组件本身不一定直接执行这些动作。
 * </p>
 */
public class RecoveryHint {

    /**
     * 建议恢复动作。
     */
    private AsyncRecoveryAction action = AsyncRecoveryAction.NONE;

    /**
     * 是否建议重试。
     */
    private boolean retryable;

    /**
     * 是否建议进入补偿系统。
     */
    private boolean compensable;

    /**
     * 是否建议触发告警。
     */
    private boolean alertRequired;

    public static RecoveryHint none() {
        return of(AsyncRecoveryAction.NONE, false, false, false);
    }

    public static RecoveryHint of(
            AsyncRecoveryAction action,
            boolean retryable,
            boolean compensable,
            boolean alertRequired
    ) {
        RecoveryHint hint = new RecoveryHint();
        hint.action = action == null ? AsyncRecoveryAction.NONE : action;
        hint.retryable = retryable;
        hint.compensable = compensable;
        hint.alertRequired = alertRequired;
        return hint;
    }

    public RecoveryHint copy() {
        return of(action, retryable, compensable, alertRequired);
    }

    public AsyncRecoveryAction getAction() {
        return action;
    }

    public void setAction(AsyncRecoveryAction action) {
        this.action = action == null ? AsyncRecoveryAction.NONE : action;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public void setRetryable(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean isCompensable() {
        return compensable;
    }

    public void setCompensable(boolean compensable) {
        this.compensable = compensable;
    }

    public boolean isAlertRequired() {
        return alertRequired;
    }

    public void setAlertRequired(boolean alertRequired) {
        this.alertRequired = alertRequired;
    }
}
