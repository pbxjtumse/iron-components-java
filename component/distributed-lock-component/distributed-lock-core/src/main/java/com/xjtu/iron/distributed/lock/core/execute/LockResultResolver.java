package com.xjtu.iron.distributed.lock.core.execute;

import com.xjtu.iron.distributed.lock.api.exception.LockLostException;
import com.xjtu.iron.distributed.lock.api.model.LockOptions;
import com.xjtu.iron.distributed.lock.api.model.LockResult;
import com.xjtu.iron.distributed.lock.api.status.LockStage;
import com.xjtu.iron.distributed.lock.api.status.LockStatus;
import com.xjtu.iron.distributed.lock.core.client.DefaultLockHandle;

import java.time.Duration;

/**
 * 合并 callback 执行结果和 release 结果，生成最终 LockResult。
 */
public final class LockResultResolver {

    public <T> LockResult<T> resolve(ExecutionOutcome<T> executionOutcome, LockReleaseOutcome releaseOutcome, DefaultLockHandle handle,
            Duration waitDuration, Duration holdDuration, LockOptions options) {
        LockStatus finalStatus = executionOutcome.getStatus();
        LockStage finalStage = executionOutcome.getStage();
        Throwable finalError = executionOutcome.getError();

        if (releaseOutcome != null && releaseOutcome.isLockLost()) {
            if (executionOutcome.getStatus() == LockStatus.SUCCESS && options.isFailOnLockLost()) {
                finalStatus = LockStatus.LOCK_LOST;
                finalStage = LockStage.RELEASE;
                finalError = new LockLostException("lock lost on release: " + handle.lockName());
            }
        } else if (releaseOutcome != null && releaseOutcome.isReleaseFailed()) {
            if (executionOutcome.getStatus() == LockStatus.SUCCESS) {
                finalStatus = LockStatus.RELEASE_FAILED;
                finalStage = LockStage.RELEASE;
                finalError = releaseOutcome.getError();
            } else if (finalError != null && releaseOutcome.getError() != null) {
                finalError.addSuppressed(releaseOutcome.getError());
            }
        }

        return LockResult.<T>builder()
                .status(finalStatus)
                .stage(finalStage)
                .acquired(true)
                .value(finalStatus == LockStatus.SUCCESS ? executionOutcome.getValue() : null)
                .handle(handle)
                .error(finalError)
                .lockName(handle.lockName())
                .lockKey(handle.lockKey())
                .ownerToken(handle.ownerToken())
                .fencingToken(handle.fencingToken().isPresent() ? handle.fencingToken().getAsLong() : null)
                .fencingTokenProviderName(handle.fencingTokenProviderName().orElse(null))
                .waitDuration(waitDuration)
                .holdDuration(holdDuration)
                .build();
    }
}
