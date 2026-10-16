package com.xjtu.iron.idempotent.core.state;

import com.xjtu.iron.idempotent.api.execution.IdempotencyResultStatus;
import com.xjtu.iron.idempotent.api.repository.acquire.IdempotencyAcquireStatus;
import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultIdempotencyStateMachineTest {

    private final DefaultIdempotencyStateMachine stateMachine =
            new DefaultIdempotencyStateMachine();

    @Test
    void acquireResultShouldBeInterpretedAfterRepositoryAtomicDecision() {
        assertThat(stateMachine.onAcquire(IdempotencyAcquireStatus.ACQUIRED).action())
                .isEqualTo(IdempotencyStateAction.EXECUTE);
        assertThat(stateMachine.onAcquire(IdempotencyAcquireStatus.SUCCESS).action())
                .isEqualTo(IdempotencyStateAction.REPLAY);
        assertThat(stateMachine.onAcquire(IdempotencyAcquireStatus.PROCESSING_EXPIRED).resultStatus())
                .isEqualTo(IdempotencyResultStatus.PROCESSING_EXPIRED);
    }

    @Test
    void staleRecoveryCandidateMustNeverExecute() {
        IdempotencyStateDecision decision =
                stateMachine.onRecovery(IdempotencyRecoveryStatus.STALE_CANDIDATE);

        assertThat(decision.action()).isEqualTo(IdempotencyStateAction.RETURN);
        assertThat(decision.resultStatus())
                .isEqualTo(IdempotencyResultStatus.STALE_RECOVERY_CANDIDATE);
    }
}
