package com.xjtu.iron.idempotent.provider.jdbc.repository;

import com.xjtu.iron.idempotent.api.policy.IdempotencyMode;
import com.xjtu.iron.idempotent.api.policy.IdempotencyWindowPolicy;
import com.xjtu.iron.idempotent.api.recovery.IdempotencyRecoveryMode;
import com.xjtu.iron.idempotent.api.repository.IdempotencyRecord;
import com.xjtu.iron.idempotent.api.repository.acquire.IdempotencyAcquireRequest;
import com.xjtu.iron.idempotent.api.repository.acquire.IdempotencyAcquireResult;
import com.xjtu.iron.idempotent.api.repository.acquire.IdempotencyAcquireStatus;
import com.xjtu.iron.idempotent.api.repository.recovery.*;
import com.xjtu.iron.idempotent.api.repository.write.*;
import com.xjtu.iron.idempotent.api.state.IdempotencyStatus;
import com.xjtu.iron.idempotent.api.storage.IdempotencyStorageContext;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcIdempotencyRepositoryTest {

    private static final IdempotencyStorageContext STORAGE = IdempotencyStorageContext.of("test-store", 101L, 7);
    private JdbcIdempotencyRepository repository;

    @BeforeEach
    void setup() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:idem" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (Connection connection = dataSource.getConnection()) {
            String sql = new String(getClass().getResourceAsStream("/META-INF/iron-idempotency/jdbc/schema-h2.sql").readAllBytes(),
                    StandardCharsets.UTF_8);
            for (String statement : sql.split(";")) {
                if (!statement.isBlank()) connection.createStatement().execute(statement);
            }
        }
        repository = new JdbcIdempotencyRepository(dataSource, "iron_idempotency_record");
    }

    @Test
    void normalRequestSeesActiveProcessing() {
        Instant now = Instant.now();
        assertThat(repository.tryAcquire(acquire("A", now, Duration.ofSeconds(30))).getStatus())
                .isEqualTo(IdempotencyAcquireStatus.ACQUIRED);
        assertThat(repository.tryAcquire(acquire("B", now.plusMillis(1), Duration.ofSeconds(30))).getStatus())
                .isEqualTo(IdempotencyAcquireStatus.PROCESSING_ACTIVE);
    }

    @Test
    void normalRequestDoesNotTakeOverExpiredProcessing() {
        Instant now = Instant.now();
        IdempotencyAcquireResult first = repository.tryAcquire(acquire("A", now, Duration.ofMillis(10)));
        IdempotencyAcquireResult second = repository.tryAcquire(acquire("B", now.plusSeconds(1), Duration.ofSeconds(30)));
        assertThat(second.getStatus()).isEqualTo(IdempotencyAcquireStatus.PROCESSING_EXPIRED);
        assertThat(second.getRecord().getOwnerToken()).isEqualTo("A");
        assertThat(second.getRecord().getVersion()).isEqualTo(first.getRecord().getVersion());
    }

    @Test
    void recoverCanTakeOverExpiredProcessingAndRejectStaleTask() {
        Instant now = Instant.now();
        IdempotencyAcquireResult first = repository.tryAcquire(acquire("A", now, Duration.ofMillis(10)));
        IdempotencyRecoveryResult recovered = repository.tryRecover(recover("B", first.getRecord(), now.plusSeconds(1)));
        assertThat(recovered.getStatus()).isEqualTo(IdempotencyRecoveryStatus.RECOVERY_ACQUIRED);
        assertThat(recovered.getRecord().getOwnerToken()).isEqualTo("B");
        assertThat(recovered.getRecord().getVersion()).isEqualTo(2);
        assertThat(repository.tryRecover(recover("C", first.getRecord(), now.plusSeconds(2))).getStatus())
                .isEqualTo(IdempotencyRecoveryStatus.STALE_CANDIDATE);
    }

    @Test
    void retryableFailedIsDecisionOnlyUntilRecoverIsCalled() {
        Instant now = Instant.now();
        IdempotencyAcquireResult first = repository.tryAcquire(acquire("A", now, Duration.ofSeconds(30)));
        repository.markFailed(new IdempotencyFailureRequest(STORAGE, "n", "k", "A", first.getRecord().getVersion(),
                new IdempotencyFailureInfo("TEMP", "temporary", true, now.plusSeconds(1)), IdempotencyMode.DURABLE, null,
                IdempotencyWindowPolicy.FIXED_FROM_FIRST_ACQUIRE, Duration.ZERO, now.plusSeconds(1)));

        IdempotencyAcquireResult normal = repository.tryAcquire(acquire("B", now.plusSeconds(2), Duration.ofSeconds(30)));
        assertThat(normal.getStatus()).isEqualTo(IdempotencyAcquireStatus.FAILED_RETRYABLE);
        IdempotencyRecoveryResult recovered = repository.tryRecover(recover("B", normal.getRecord(), now.plusSeconds(3)));
        assertThat(recovered.getStatus()).isEqualTo(IdempotencyRecoveryStatus.RECOVERY_ACQUIRED);
        assertThat(recovered.getRecord().getVersion()).isEqualTo(2);
    }

    @Test
    void discardedIsTerminalAndCannotBeRecovered() {
        Instant now = Instant.now();
        IdempotencyAcquireResult first = repository.tryAcquire(acquire("A", now, Duration.ofSeconds(30)));
        IdempotencyWriteResult discarded = repository.markDiscarded(new IdempotencyDiscardRequest(
                STORAGE, "n", "k", "A", first.getRecord().getVersion(), "discard-reason", IdempotencyMode.DURABLE,
                null, IdempotencyWindowPolicy.FIXED_FROM_FIRST_ACQUIRE, Duration.ZERO, now.plusSeconds(1)));

        assertThat(discarded.getStatus()).isEqualTo(IdempotencyWriteStatus.UPDATED);
        assertThat(discarded.getRecord().getStatus()).isEqualTo(IdempotencyStatus.DISCARDED);
        assertThat(repository.tryAcquire(acquire("B", now.plusSeconds(2), Duration.ofSeconds(30))).getStatus())
                .isEqualTo(IdempotencyAcquireStatus.DISCARDED);
        assertThat(repository.tryRecover(recover("B", discarded.getRecord(), now.plusSeconds(3))).getStatus())
                .isEqualTo(IdempotencyRecoveryStatus.DISCARDED);
    }

    @Test
    void storageRoutingMetadataMustRemainStable() {
        Instant now = Instant.now();
        repository.tryAcquire(acquire("A", now, Duration.ofSeconds(30)));
        IdempotencyStorageContext changedShard = IdempotencyStorageContext.of("test-store", 202L, 7);
        IdempotencyAcquireResult result = repository.tryAcquire(acquire(changedShard, "B", now.plusMillis(1), Duration.ofSeconds(30)));
        assertThat(result.getStatus()).isEqualTo(IdempotencyAcquireStatus.KEY_CONFLICT);
    }

    @Test
    void recoveryScanUsesStoreAndScanBucket() {
        Instant now = Instant.now();
        repository.tryAcquire(acquire("A", now, Duration.ofMillis(10)));
        repository.tryAcquire(acquire(IdempotencyStorageContext.of("other-store", 101L, 7), "B", now, Duration.ofMillis(10)));

        List<IdempotencyRecoveryCandidate> candidates = repository.findRecoveryCandidates(
                new IdempotencyRecoveryQuery("test-store", "n", 7, now.plusSeconds(1), 20));
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getStoreName()).isEqualTo("test-store");
        assertThat(candidates.get(0).getScanBucket()).isEqualTo(7);
    }

    @Test
    void expiredProcessingMustNotExtendSlidingWindowOnNormalDuplicate() {
        Instant now = Instant.parse("2026-08-17T00:00:00Z");
        IdempotencyAcquireRequest firstRequest = new IdempotencyAcquireRequest(STORAGE, "window", "sliding", "hash", "merchant:1", "A",
                IdempotencyMode.WINDOWED, Duration.ofSeconds(1), Duration.ofMinutes(5), IdempotencyWindowPolicy.SLIDING_ON_ACCESS,
                Duration.ofMinutes(1), IdempotencyRecoveryMode.NONE, now);
        IdempotencyAcquireResult first = repository.tryAcquire(firstRequest);
        Instant originalWindowExpireAt = first.getRecord().getWindowExpireAt();

        IdempotencyAcquireRequest duplicate = new IdempotencyAcquireRequest(STORAGE, "window", "sliding", "hash", "merchant:1", "B",
                IdempotencyMode.WINDOWED, Duration.ofSeconds(1), Duration.ofMinutes(5), IdempotencyWindowPolicy.SLIDING_ON_ACCESS,
                Duration.ofMinutes(1), IdempotencyRecoveryMode.NONE, now.plusSeconds(2));
        IdempotencyAcquireResult result = repository.tryAcquire(duplicate);
        assertThat(result.getStatus()).isEqualTo(IdempotencyAcquireStatus.PROCESSING_EXPIRED);
        assertThat(result.getRecord().getWindowExpireAt()).isEqualTo(originalWindowExpireAt);
    }

    private IdempotencyAcquireRequest acquire(String owner, Instant now, Duration timeout) {
        return acquire(STORAGE, owner, now, timeout);
    }

    private IdempotencyAcquireRequest acquire(IdempotencyStorageContext storage, String owner, Instant now, Duration timeout) {
        return new IdempotencyAcquireRequest(storage, "n", "k", "hash", "merchant:1", owner, IdempotencyMode.DURABLE,
                timeout, null, IdempotencyWindowPolicy.FIXED_FROM_FIRST_ACQUIRE, Duration.ZERO,
                IdempotencyRecoveryMode.EXTERNAL_TASK, now);
    }

    private IdempotencyRecoveryAcquireRequest recover(String newOwner, IdempotencyRecord candidate, Instant now) {
        return new IdempotencyRecoveryAcquireRequest(candidate.storageContext(), "n", "k", "hash", "merchant:1", newOwner,
                candidate.getOwnerToken(), candidate.getVersion(), IdempotencyMode.DURABLE, Duration.ofSeconds(30), true, true, now);
    }
}
