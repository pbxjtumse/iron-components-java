package com.xjtu.iron.idempotent.core.execution;

import com.xjtu.iron.idempotent.api.execution.IdempotencyRequest;
import com.xjtu.iron.idempotent.api.execution.IdempotencyResult;
import com.xjtu.iron.idempotent.api.execution.IdempotencyResultStatus;
import com.xjtu.iron.idempotent.api.policy.IdempotencyMode;
import com.xjtu.iron.idempotent.api.policy.IdempotencyPolicy;
import com.xjtu.iron.idempotent.api.policy.IdempotencyWindowPolicy;
import com.xjtu.iron.idempotent.api.recovery.IdempotencyRecoveryPolicy;
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
import com.xjtu.iron.idempotent.api.result.IdempotencyResultPolicies;
import com.xjtu.iron.idempotent.api.state.IdempotencyStatus;
import com.xjtu.iron.idempotent.api.storage.IdempotencyStorageContext;
import com.xjtu.iron.idempotent.core.policy.DefaultIdempotencyPolicyRegistry;
import com.xjtu.iron.idempotent.core.policy.IdempotencyPolicyRegistry;
import com.xjtu.iron.idempotent.core.repository.DefaultIdempotencyRepositoryRegistry;
import com.xjtu.iron.idempotent.core.repository.IdempotencyRepositoryRegistry;
import com.xjtu.iron.idempotent.core.state.DefaultIdempotencyStateMachine;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultIdempotencyExecutorTest {

    private static final IdempotencyStorageContext STORAGE = IdempotencyStorageContext.of("order-store", 1001L, 17);

    @Test
    void duplicateSuccessWithoutResultPolicyShouldReplayWithoutExecutingAgain() {
        MemoryRepository repository = new MemoryRepository();
        DefaultIdempotencyExecutor executor = executor(repository);
        int[] calls = {0};
        IdempotencyRequest request = request("order:1");

        IdempotencyResult<String> first = executor.execute(request, ctx -> { calls[0]++; return "ok"; });
        IdempotencyResult<String> second = executor.execute(request, ctx -> { calls[0]++; return "should-not-run"; });

        assertThat(first.getStatus()).isEqualTo(IdempotencyResultStatus.EXECUTED);
        assertThat(first.getRecord().getStoreName()).isEqualTo("order-store");
        assertThat(second.getStatus()).isEqualTo(IdempotencyResultStatus.REPLAYED);
        assertThat(second.getValue()).isNull();
        assertThat(calls[0]).isEqualTo(1);
    }

    @Test
    void snapshotPolicyShouldReplayTypedValueWithoutClassParameter() {
        MemoryRepository repository = new MemoryRepository();
        DefaultIdempotencyExecutor executor = executor(repository);
        var snapshot = IdempotencyResultPolicies.snapshot(new com.xjtu.iron.idempotent.api.result.IdempotencyResultSerializer<String>() {
            @Override public String serialize(String value) { return value; }
            @Override public String deserialize(String payload) { return payload; }
        });

        IdempotencyResult<String> first = executor.execute(request("snapshot:1"), snapshot, ctx -> "first-result");
        IdempotencyResult<String> replay = executor.execute(request("snapshot:1"), snapshot, ctx -> "never");
        assertThat(first.getStatus()).isEqualTo(IdempotencyResultStatus.EXECUTED);
        assertThat(replay.getStatus()).isEqualTo(IdempotencyResultStatus.REPLAYED);
        assertThat(replay.getValue()).isEqualTo("first-result");
    }

    @Test
    void referencePolicyShouldPersistStableReferenceAndResolveReplay() {
        MemoryRepository repository = new MemoryRepository();
        DefaultIdempotencyExecutor executor = executor(repository);
        Map<String, String> business = new HashMap<>();
        var reference = IdempotencyResultPolicies.reference(new com.xjtu.iron.idempotent.api.result.IdempotencyResultReference<String>() {
            @Override
            public String capture(String value) {
                String id = value.substring(value.indexOf(':') + 1);
                business.put(id, value);
                return id;
            }
            @Override public String resolve(String storedReference) { return "resolved:" + business.get(storedReference); }
        });

        IdempotencyRequest request = request("reference:1");
        IdempotencyResult<String> first = executor.execute(request, reference, ctx -> "created:1001");
        IdempotencyResult<String> replay = executor.execute(request, reference, ctx -> "never");
        assertThat(first.getStatus()).isEqualTo(IdempotencyResultStatus.EXECUTED);
        assertThat(replay.getStatus()).isEqualTo(IdempotencyResultStatus.REPLAYED);
        assertThat(replay.getValue()).isEqualTo("resolved:created:1001");
    }

    @Test
    void replayWithDifferentResultPolicyShouldBeRejectedWithoutBusinessReexecution() {
        MemoryRepository repository = new MemoryRepository();
        DefaultIdempotencyExecutor executor = executor(repository);
        int[] calls = {0};
        var snapshot = IdempotencyResultPolicies.snapshot(new com.xjtu.iron.idempotent.api.result.IdempotencyResultSerializer<String>() {
            @Override public String serialize(String value) { return value; }
            @Override public String deserialize(String payload) { return payload; }
        });
        var reference = IdempotencyResultPolicies.reference(new com.xjtu.iron.idempotent.api.result.IdempotencyResultReference<String>() {
            @Override public String capture(String value) { return value; }
            @Override public String resolve(String storedReference) { return storedReference; }
        });

        IdempotencyRequest request = request("policy-mismatch:1");
        executor.execute(request, snapshot, ctx -> { calls[0]++; return "snapshot-value"; });
        IdempotencyResult<String> replay = executor.execute(request, reference, ctx -> { calls[0]++; return "must-not-run"; });
        assertThat(replay.getStatus()).isEqualTo(IdempotencyResultStatus.RESULT_POLICY_MISMATCH);
        assertThat(calls[0]).isEqualTo(1);
    }

    @Test
    void historicalDiscardedShouldReturnWithoutExecutingBusiness() {
        MemoryRepository repository = new MemoryRepository();
        IdempotencyRequest request = request("discarded:1");
        IdempotencyAcquireRequest acquire = new IdempotencyAcquireRequest(STORAGE, "default", request.getKey(), request.getRequestHash(),
                request.getRouteKey(), "A", IdempotencyMode.DURABLE, java.time.Duration.ofSeconds(30), null,
                IdempotencyWindowPolicy.FIXED_FROM_FIRST_ACQUIRE, java.time.Duration.ZERO,
                com.xjtu.iron.idempotent.api.recovery.IdempotencyRecoveryMode.EXTERNAL_TASK, Instant.parse("2026-08-17T00:00:00Z"));
        IdempotencyRecord record = repository.tryAcquire(acquire).getRecord();
        repository.markDiscarded(new IdempotencyDiscardRequest(STORAGE, "default", request.getKey(), "A", record.getVersion(), null,
                IdempotencyMode.DURABLE, null, IdempotencyWindowPolicy.FIXED_FROM_FIRST_ACQUIRE,
                java.time.Duration.ZERO, Instant.parse("2026-08-17T00:00:01Z")));

        int[] calls = {0};
        IdempotencyResult<String> result = executor(repository).execute(request, ctx -> { calls[0]++; return "never"; });
        assertThat(result.getStatus()).isEqualTo(IdempotencyResultStatus.PREVIOUS_DISCARDED);
        assertThat(calls[0]).isZero();
    }

    private IdempotencyRequest request(String key) {
        return IdempotencyRequest.builder().key(key).routeKey("merchant:1").requestHash("hash-" + key)
                .storeName(STORAGE.getStoreName()).shardKey(STORAGE.getShardKey()).scanBucket(STORAGE.getScanBucket())
                .policyName("test-durable").build();
    }

    private DefaultIdempotencyExecutor executor(IdempotencyRepository repository) {
        IdempotencyRepositoryRegistry repositoryRegistry = new DefaultIdempotencyRepositoryRegistry(List.of(repository), "mem", "mem");
        IdempotencyPolicy policy = IdempotencyPolicy.builder().name("test-durable").mode(IdempotencyMode.DURABLE)
                .processingTimeout(java.time.Duration.ofSeconds(1)).recoveryPolicy(IdempotencyRecoveryPolicy.externalTask()).build();
        IdempotencyPolicyRegistry policyRegistry = new DefaultIdempotencyPolicyRegistry(List.of(policy), "test-durable");
        return new DefaultIdempotencyExecutor(repositoryRegistry, policyRegistry,
                (namespace, key) -> UUID.randomUUID().toString(),
                (error, at) -> new IdempotencyFailureInfo("BUSINESS_ERROR", error.getMessage(), false, at),
                null, null, new DefaultIdempotencyStateMachine(), null, null,
                Clock.fixed(Instant.parse("2026-08-17T00:00:00Z"), ZoneOffset.UTC));
    }

    private static final class MemoryRepository implements IdempotencyRepository {
        private final Map<String, IdempotencyRecord> data = new HashMap<>();

        @Override public String providerName() { return "mem"; }
        @Override
        public IdempotencyRepositoryCapabilities capabilities() {
            return IdempotencyRepositoryCapabilities.builder().windowedSupported(true).durableSupported(true)
                    .resultPayloadSupported(true).businessTransactionParticipationSupported(false).recoveryQuerySupported(false).build();
        }

        @Override
        public synchronized IdempotencyAcquireResult tryAcquire(IdempotencyAcquireRequest r) {
            String identity = identity(r.getStorageContext(), r.getNamespace(), r.getKey());
            IdempotencyRecord current = data.get(identity);
            if (current == null) {
                current = IdempotencyRecord.builder().storeName(r.getStorageContext().getStoreName())
                        .shardKey(r.getStorageContext().getShardKey()).scanBucket(r.getStorageContext().getScanBucket())
                        .namespace(r.getNamespace()).key(r.getKey()).routeKey(r.getRouteKey()).requestHash(r.getRequestHash())
                        .status(IdempotencyStatus.PROCESSING).ownerToken(r.getOwnerToken()).version(1)
                        .recoveryMode(r.getRecoveryMode()).windowPolicy(r.getWindowPolicy())
                        .processingExpireAt(r.getNow().plus(r.getProcessingTimeout())).createdAt(r.getNow()).updatedAt(r.getNow()).build();
                data.put(identity, current);
                return IdempotencyAcquireResult.acquired(current, false);
            }
            return switch (current.getStatus()) {
                case SUCCESS -> IdempotencyAcquireResult.of(IdempotencyAcquireStatus.SUCCESS, current);
                case DISCARDED -> IdempotencyAcquireResult.of(IdempotencyAcquireStatus.DISCARDED, current);
                case FAILED -> IdempotencyAcquireResult.of(current.isFailureRetryable()
                        ? IdempotencyAcquireStatus.FAILED_RETRYABLE : IdempotencyAcquireStatus.FAILED_FINAL, current);
                case PROCESSING -> IdempotencyAcquireResult.of(current.getProcessingExpireAt().isAfter(r.getNow())
                        ? IdempotencyAcquireStatus.PROCESSING_ACTIVE : IdempotencyAcquireStatus.PROCESSING_EXPIRED, current);
            };
        }

        @Override public IdempotencyRecoveryResult tryRecover(IdempotencyRecoveryAcquireRequest request) {
            return IdempotencyRecoveryResult.of(IdempotencyRecoveryStatus.NOT_RECOVERABLE, null);
        }

        @Override
        public synchronized IdempotencyWriteResult markSuccess(IdempotencySuccessRequest r) {
            IdempotencyRecord current = data.get(identity(r.getStorageContext(), r.getNamespace(), r.getKey()));
            IdempotencyRecord next = completed(current, IdempotencyStatus.SUCCESS, r.getResultPayload(), r.getNow());
            data.put(identity(r.getStorageContext(), r.getNamespace(), r.getKey()), next);
            return IdempotencyWriteResult.of(IdempotencyWriteStatus.UPDATED, next);
        }

        @Override public IdempotencyWriteResult markFailed(IdempotencyFailureRequest request) {
            return IdempotencyWriteResult.of(IdempotencyWriteStatus.UPDATED,
                    data.get(identity(request.getStorageContext(), request.getNamespace(), request.getKey())));
        }

        @Override
        public synchronized IdempotencyWriteResult markDiscarded(IdempotencyDiscardRequest r) {
            IdempotencyRecord current = data.get(identity(r.getStorageContext(), r.getNamespace(), r.getKey()));
            IdempotencyRecord next = completed(current, IdempotencyStatus.DISCARDED, r.getResultPayload(), r.getNow());
            data.put(identity(r.getStorageContext(), r.getNamespace(), r.getKey()), next);
            return IdempotencyWriteResult.of(IdempotencyWriteStatus.UPDATED, next);
        }

        @Override
        public Optional<IdempotencyRecord> find(IdempotencyStorageContext storageContext, String namespace, String key) {
            return Optional.ofNullable(data.get(identity(storageContext, namespace, key)));
        }

        private IdempotencyRecord completed(IdempotencyRecord current, IdempotencyStatus status, String resultPayload, Instant now) {
            return IdempotencyRecord.builder().storeName(current.getStoreName()).shardKey(current.getShardKey()).scanBucket(current.getScanBucket())
                    .namespace(current.getNamespace()).key(current.getKey()).routeKey(current.getRouteKey()).requestHash(current.getRequestHash())
                    .status(status).ownerToken(current.getOwnerToken()).version(current.getVersion()).resultPayload(resultPayload)
                    .recoveryMode(current.getRecoveryMode()).windowPolicy(current.getWindowPolicy()).updatedAt(now).completedAt(now).build();
        }

        private String identity(IdempotencyStorageContext storage, String namespace, String key) {
            return storage.getStoreName() + "|" + namespace + "|" + key;
        }
    }
}
