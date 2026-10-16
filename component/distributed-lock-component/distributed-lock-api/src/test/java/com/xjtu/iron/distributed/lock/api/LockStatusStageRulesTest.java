package com.xjtu.iron.distributed.lock.api;

import com.xjtu.iron.distributed.lock.api.model.LockResult;
import com.xjtu.iron.distributed.lock.api.status.LockStage;
import com.xjtu.iron.distributed.lock.api.status.LockStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LockStatusStageRulesTest {

    @Test
    void validCombinationsShouldPass() {
        assertTrue(LockStatusStageRules.isValid(LockStatus.NOT_ACQUIRED, LockStage.ACQUIRE, false));
        assertTrue(LockStatusStageRules.isValid(LockStatus.NOT_ACQUIRED, LockStage.WAIT, false));
        assertTrue(LockStatusStageRules.isValid(LockStatus.RELEASE_FAILED, LockStage.RELEASE, true));
    }

    @Test
    void invalidCombinationsShouldFail() {
        assertFalse(LockStatusStageRules.isValid(LockStatus.SUCCESS, LockStage.RELEASE, true));
        assertFalse(LockStatusStageRules.isValid(LockStatus.PROVIDER_ERROR, LockStage.RELEASE, true));
        assertThrows(IllegalArgumentException.class, () -> LockResult.builder()
                .status(LockStatus.SUCCESS)
                .stage(LockStage.ACQUIRE)
                .acquired(true)
                .build());
    }

    @Test
    void providerErrorAtFencingShouldBeValidAfterLockWasAcquired() {
        assertTrue(LockStatusStageRules.isValid(LockStatus.PROVIDER_ERROR, LockStage.FENCING, true));
        assertFalse(LockStatusStageRules.isValid(LockStatus.PROVIDER_ERROR, LockStage.FENCING, false));
    }
}
