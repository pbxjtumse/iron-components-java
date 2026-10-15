package com.xjtu.iron.distributed.lock.core.execute;

import com.xjtu.iron.distributed.lock.api.status.LockStage;
import com.xjtu.iron.distributed.lock.spi.protocol.release.LockReleaseResponse;
import com.xjtu.iron.distributed.lock.spi.protocol.release.LockReleaseStatus;

/** Core 对 release 动作的解释结果。 */
public final class LockReleaseOutcome {

    public enum Type {
        RELEASED, LOCK_LOST, RELEASE_FAILED, ALREADY_ATTEMPTED
    }

    private final Type type;
    private final LockStage stage;
    private final Throwable error;
    private final LockReleaseResponse providerResponse;

    private LockReleaseOutcome(Type type, LockStage stage, Throwable error, LockReleaseResponse providerResponse) {
        this.type = type;
        this.stage = stage;
        this.error = error;
        this.providerResponse = providerResponse;
    }

    public static LockReleaseOutcome released(LockReleaseResponse response) {
        return new LockReleaseOutcome(Type.RELEASED, LockStage.RELEASE, null, response);
    }

    public static LockReleaseOutcome lockLost(LockReleaseResponse response) {
        return new LockReleaseOutcome(Type.LOCK_LOST, LockStage.RELEASE, null, response);
    }

    public static LockReleaseOutcome releaseFailed(LockReleaseResponse response) {
        return new LockReleaseOutcome(Type.RELEASE_FAILED, LockStage.RELEASE, response == null ? null : response.getError(), response);
    }

    public static LockReleaseOutcome alreadyAttempted() {
        return new LockReleaseOutcome(Type.ALREADY_ATTEMPTED, LockStage.RELEASE, null, null);
    }

    public static LockReleaseOutcome fromProviderResponse(LockReleaseResponse response) {
        if (response == null || response.getStatus() == null) {
            return new LockReleaseOutcome(Type.RELEASE_FAILED, LockStage.RELEASE, null, response);
        }
        LockReleaseStatus status = response.getStatus();
        switch (status) {
            case RELEASED:
                return released(response);
            case NOT_FOUND:
            case NOT_OWNER:
                return lockLost(response);
            case PROVIDER_ERROR:
            default:
                return releaseFailed(response);
        }
    }

    public Type getType() {
        return type;
    }

    public LockStage getStage() {
        return stage;
    }

    public Throwable getError() {
        return error;
    }

    public LockReleaseResponse getProviderResponse() {
        return providerResponse;
    }

    public boolean isReleased() {
        return type == Type.RELEASED;
    }

    public boolean isAlreadyAttempted() {
        return type == Type.ALREADY_ATTEMPTED;
    }

    public boolean isLockLost() {
        return type == Type.LOCK_LOST;
    }

    public boolean isReleaseFailed() {
        return type == Type.RELEASE_FAILED;
    }

    public boolean isSafeNoop() {
        return isReleased() || isAlreadyAttempted();
    }
}
