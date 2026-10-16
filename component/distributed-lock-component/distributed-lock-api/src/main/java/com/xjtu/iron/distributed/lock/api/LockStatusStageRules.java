package com.xjtu.iron.distributed.lock.api;

import com.xjtu.iron.distributed.lock.api.status.LockStage;
import com.xjtu.iron.distributed.lock.api.status.LockStatus;

import java.util.Objects;

/**
 * LockStatus 与 LockStage 的合法组合规则。
 *
 * <p>
 * LockStatus 是最终结果，LockStage 是决定最终结果的阶段。二者不是笛卡尔积关系，
 * 例如 SUCCESS 只能出现在 EXECUTE 阶段，RELEASE_FAILED 只能出现在 RELEASE 阶段。
 * </p>
 */
public final class LockStatusStageRules {

    private LockStatusStageRules() {
    }

    /**
     * 校验 LockResult 的 status/stage/acquired 组合是否合法。
     */
    public static void validate(LockStatus status, LockStage stage, boolean acquired) {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(stage, "stage must not be null");
        if (!isValid(status, stage, acquired)) {
            throw new IllegalArgumentException("invalid LockResult combination: status=" + status + ", stage=" + stage + ", acquired=" + acquired);
        }
    }

    /**
     * 判断组合是否合法。
     */
    public static boolean isValid(LockStatus status, LockStage stage, boolean acquired) {
        if (status == null || stage == null) {
            return false;
        }
        switch (status) {
            case INVALID_OPTIONS:
                return stage == LockStage.VALIDATE && !acquired;
            case ACQUIRED:
                return stage == LockStage.ACQUIRE && acquired;
            case NOT_ACQUIRED:
                return (stage == LockStage.ACQUIRE || stage == LockStage.WAIT) && !acquired;
            case SUCCESS:
                return stage == LockStage.EXECUTE && acquired;
            case EXECUTION_FAILED:
                return stage == LockStage.EXECUTE && acquired;
            case LOCK_LOST:
                return (stage == LockStage.RENEW || stage == LockStage.CHECK || stage == LockStage.RELEASE) && acquired;
            case FENCING_REJECTED:
                return stage == LockStage.FENCING && acquired;
            case RELEASE_FAILED:
                return stage == LockStage.RELEASE && acquired;
            case PROVIDER_ERROR:
                if (stage == LockStage.ACQUIRE) {
                    return !acquired;
                }
                return (stage == LockStage.FENCING || stage == LockStage.RENEW || stage == LockStage.CHECK) && acquired;
            default:
                return false;
        }
    }
}
