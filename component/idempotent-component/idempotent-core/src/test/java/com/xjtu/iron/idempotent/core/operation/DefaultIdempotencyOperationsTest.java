package com.xjtu.iron.idempotent.core.operation;

import com.xjtu.iron.idempotent.api.operation.acquire.IdempotencyOperationAcquireCommand;
import com.xjtu.iron.idempotent.api.operation.acquire.IdempotencyOperationAcquireResult;
import com.xjtu.iron.idempotent.api.operation.acquire.IdempotencyOperationAcquireStatus;
import com.xjtu.iron.idempotent.api.operation.write.IdempotencyCompletionCommand;
import com.xjtu.iron.idempotent.api.operation.write.IdempotencyOperationWriteResult;
import com.xjtu.iron.idempotent.api.operation.write.IdempotencyOperationWriteStatus;
import com.xjtu.iron.idempotent.api.policy.IdempotencyMode;
import com.xjtu.iron.idempotent.api.policy.IdempotencyPolicy;
import com.xjtu.iron.idempotent.api.repository.IdempotencyRecord;
import com.xjtu.iron.idempotent.api.repository.IdempotencyRepository;
import com.xjtu.iron.idempotent.api.repository.IdempotencyRepositoryCapabilities;
import com.xjtu.iron.idempotent.api.repository.acquire.IdempotencyAcquireRequest;
import com.xjtu.iron.idempotent.api.repository.acquire.IdempotencyAcquireResult;
import com.xjtu.iron.idempotent.api.repository.acquire.IdempotencyAcquireStatus;
import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryAcquireRequest;
import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryResult;
import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryStatus;
import com.xjtu.iron.idempotent.api.repository.write.*;
import com.xjtu.iron.idempotent.api.state.IdempotencyStatus;
import com.xjtu.iron.idempotent.api.storage.IdempotencyStorageContext;
import com.xjtu.iron.idempotent.core.policy.DefaultIdempotencyPolicyRegistry;
import com.xjtu.iron.idempotent.core.repository.DefaultIdempotencyRepositoryRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultIdempotencyOperationsTest {

    private static final IdempotencyStorageContext STORAGE = IdempotencyStorageContext.of("message-consume", 10001L, 33);

    @Test
    void acquireShouldExposeDuplicateDiscardedAndPassStorageContextToRepository() {
        StubRepository repository = new StubRepository();
        repository.acquireStatus = IdempotencyAcquireStatus.DISCARDED;
        DefaultIdempotencyOperations operations = operations(repository);

        IdempotencyOperationAcquireResult result = operations.acquire(IdempotencyOperationAcquireCommand.builder()
                .key("msg-1").requestHash("hash").routeKey("topic-a").storageContext(STORAGE)
                .ownerToken("owner-A").policyName("message").build());

        assertThat(result.getStatus()).isEqualTo(IdempotencyOperationAcquireStatus.DUPLICATE_DISCARDED);
        assertThat(repository.lastAcquire.getStorageContext().getStoreName()).isEqualTo("message-consume");
        assertThat(repository.lastAcquire.getStorageContext().getShardKey()).isEqualTo(10001L);
        assertThat(repository.lastAcquire.getStorageContext().getScanBucket()).isEqualTo(33);
    }

    @Test
    void markDiscardedShouldUseOwnerVersionCasContract() {
        StubRepository repository = new StubRepository();
        DefaultIdempotencyOperations operations = operations(repository);

        IdempotencyOperationWriteResult result = operations.markDiscarded(IdempotencyCompletionCommand.builder()
                .key("msg-1").storageContext(STORAGE).ownerToken("owner-A").version(7)
                .resultPayload("DISCARDED:PERMANENT_ERROR").policyName("message").build());

        assertThat(result.getStatus()).isEqualTo(IdempotencyOperationWriteStatus.UPDATED);
        assertThat(repository.lastDiscard.getOwnerToken()).isEqualTo("owner-A");
        assertThat(repository.lastDiscard.getVersion()).isEqualTo(7);
        assertThat(repository.lastDiscard.getStorageContext().getScanBucket()).isEqualTo(33);
    }

    private DefaultIdempotencyOperations operations(StubRepository repository) {
        IdempotencyPolicy policy = IdempotencyPolicy.builder().name("message").mode(IdempotencyMode.DURABLE)
                .namespace("message-consume").repositoryName("stub").build();
        return new DefaultIdempotencyOperations(
                new DefaultIdempotencyRepositoryRegistry(List.of(repository), "stub", "stub"),
                new DefaultIdempotencyPolicyRegistry(List.of(policy), "message"),
                Clock.fixed(Instant.parse("2026-09-03T00:00:00Z"), ZoneOffset.UTC));
    }

    private static final class StubRepository implements IdempotencyRepository {
        private IdempotencyAcquireStatus acquireStatus = IdempotencyAcquireStatus.ACQUIRED;
        private IdempotencyAcquireRequest lastAcquire;
        private IdempotencyDiscardRequest lastDiscard;

        @Override public String providerName() { return "stub"; }
        @Override
        public IdempotencyRepositoryCapabilities capabilities() {
            return IdempotencyRepositoryCapabilities.builder().durableSupported(true).windowedSupported(true)
                    .resultPayloadSupported(true).build();
        }

        @Override
        public IdempotencyAcquireResult tryAcquire(IdempotencyAcquireRequest request) {
            this.lastAcquire = request;
            return IdempotencyAcquireResult.of(acquireStatus, record(request.getStorageContext(), request.getNamespace(), request.getKey()));
        }

        @Override
        public IdempotencyRecoveryResult tryRecover(IdempotencyRecoveryAcquireRequest request) {
            return IdempotencyRecoveryResult.of(IdempotencyRecoveryStatus.NOT_RECOVERABLE, null);
        }

        @Override
        public IdempotencyWriteResult markSuccess(IdempotencySuccessRequest request) {
            return IdempotencyWriteResult.of(IdempotencyWriteStatus.UPDATED, record(request.getStorageContext(), request.getNamespace(), request.getKey()));
        }

        @Override
        public IdempotencyWriteResult markFailed(IdempotencyFailureRequest request) {
            return IdempotencyWriteResult.of(IdempotencyWriteStatus.UPDATED, record(request.getStorageContext(), request.getNamespace(), request.getKey()));
        }

        @Override
        public IdempotencyWriteResult markDiscarded(IdempotencyDiscardRequest request) {
            this.lastDiscard = request;
            IdempotencyRecord record = IdempotencyRecord.builder().storeName(request.getStorageContext().getStoreName())
                    .shardKey(request.getStorageContext().getShardKey()).scanBucket(request.getStorageContext().getScanBucket())
                    .namespace(request.getNamespace()).key(request.getKey()).status(IdempotencyStatus.DISCARDED)
                    .ownerToken(request.getOwnerToken()).version(request.getVersion()).build();
            return IdempotencyWriteResult.of(IdempotencyWriteStatus.UPDATED, record);
        }

        @Override
        public Optional<IdempotencyRecord> find(IdempotencyStorageContext storageContext, String namespace, String key) {
            return Optional.of(record(storageContext, namespace, key));
        }

        private IdempotencyRecord record(IdempotencyStorageContext storage, String namespace, String key) {
            return IdempotencyRecord.builder().storeName(storage.getStoreName()).shardKey(storage.getShardKey())
                    .scanBucket(storage.getScanBucket()).namespace(namespace).key(key).status(IdempotencyStatus.PROCESSING)
                    .ownerToken("owner-A").version(1).build();
        }
    }
}
