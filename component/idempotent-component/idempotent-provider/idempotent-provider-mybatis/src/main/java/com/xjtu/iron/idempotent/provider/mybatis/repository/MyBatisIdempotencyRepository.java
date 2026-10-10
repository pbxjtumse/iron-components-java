package com.xjtu.iron.idempotent.provider.mybatis.repository;

import com.xjtu.iron.idempotent.api.policy.IdempotencyMode;
import com.xjtu.iron.idempotent.api.policy.IdempotencyWindowPolicy;
import com.xjtu.iron.idempotent.api.recovery.IdempotencyRecoveryMode;
import com.xjtu.iron.idempotent.api.repository.IdempotencyRecord;
import com.xjtu.iron.idempotent.api.repository.IdempotencyRepository;
import com.xjtu.iron.idempotent.api.repository.IdempotencyRepositoryCapabilities;
import com.xjtu.iron.idempotent.api.repository.acquire.IdempotencyAcquireRequest;
import com.xjtu.iron.idempotent.api.repository.acquire.IdempotencyAcquireResult;
import com.xjtu.iron.idempotent.api.repository.acquire.IdempotencyAcquireStatus;
import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryAcquireRequest;
import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryCandidate;
import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryQuery;
import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryRepository;
import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryResult;
import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryStatus;
import com.xjtu.iron.idempotent.api.repository.write.IdempotencyDiscardRequest;
import com.xjtu.iron.idempotent.api.repository.write.IdempotencyFailureRequest;
import com.xjtu.iron.idempotent.api.repository.write.IdempotencySuccessRequest;
import com.xjtu.iron.idempotent.api.repository.write.IdempotencyWriteResult;
import com.xjtu.iron.idempotent.api.repository.write.IdempotencyWriteStatus;
import com.xjtu.iron.idempotent.api.state.IdempotencyStatus;
import com.xjtu.iron.idempotent.api.storage.IdempotencyStorageContext;
import com.xjtu.iron.idempotent.provider.mybatis.mapper.IdempotencyMapper;
import com.xjtu.iron.idempotent.provider.mybatis.mapping.IdempotencyRow;
import com.xjtu.iron.relational.mybatis.MyBatisAccess;
import com.xjtu.iron.relational.mybatis.MyBatisConstraintViolationDetector;
import com.xjtu.iron.relational.mybatis.MyBatisTableNameValidator;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 使用公共 MyBatis Access 实现的单物理目标幂等状态仓储。 */
public final class MyBatisIdempotencyRepository
        implements IdempotencyRepository, IdempotencyRecoveryRepository {

    public static final String PROVIDER_NAME = "mybatis";

    /** 统一提供 Mapper、当前事务校验和 REQUIRES_NEW 的公共 Access。 */
    private final MyBatisAccess access;

    /** 已通过白名单校验的幂等物理表名。 */
    private final String tableName;

    public MyBatisIdempotencyRepository(MyBatisAccess access, String tableName) {
        this.access = Objects.requireNonNull(access, "access must not be null");
        this.tableName = MyBatisTableNameValidator.requireValid(
                tableName,
                "iron_idempotency_record"
        );
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public IdempotencyRepositoryCapabilities capabilities() {
        return IdempotencyRepositoryCapabilities.builder()
                .windowedSupported(true)
                .durableSupported(true)
                .resultPayloadSupported(true)
                .businessTransactionParticipationSupported(
                        access.supportsCurrentTransactionParticipation()
                )
                .recoveryQuerySupported(true)
                .build();
    }

    /** Tx-A：在独立事务中完成唯一键竞争、行锁读取和状态判定。 */
    @Override
    public IdempotencyAcquireResult tryAcquire(IdempotencyAcquireRequest request) {
        try {
            requireStorage(request.getStorageContext());
            return access.executeInNewTransaction(
                    "idempotency.try-acquire",
                    IdempotencyMapper.class,
                    mapper -> tryAcquire(mapper, request)
            );
        } catch (Exception failure) {
            return IdempotencyAcquireResult.providerError(failure);
        }
    }

    private IdempotencyAcquireResult tryAcquire(
            IdempotencyMapper mapper,
            IdempotencyAcquireRequest request
    ) {
        IdempotencyStorageContext storage = request.getStorageContext();
        if (tryInsertProcessing(mapper, request)) {
            return IdempotencyAcquireResult.acquired(
                    selectForUpdate(mapper, storage, request.getNamespace(), request.getKey()),
                    false
            );
        }

        IdempotencyRecord current = selectForUpdate(
                mapper,
                storage,
                request.getNamespace(),
                request.getKey()
        );
        if (current == null) {
            return IdempotencyAcquireResult.providerError(
                    new IllegalStateException("idempotency record disappeared after duplicate key")
            );
        }
        if (storageConflict(current, storage)) {
            return IdempotencyAcquireResult.of(IdempotencyAcquireStatus.KEY_CONFLICT, current);
        }
        if (isWindowExpired(current, request.getNow())) {
            return IdempotencyAcquireResult.acquired(
                    restartWindow(mapper, current, request),
                    true
            );
        }
        if (routeConflict(current.getRouteKey(), request.getRouteKey())
                || hashConflict(current.getRequestHash(), request.getRequestHash())) {
            return IdempotencyAcquireResult.of(IdempotencyAcquireStatus.KEY_CONFLICT, current);
        }

        return switch (current.getStatus()) {
            case SUCCESS -> IdempotencyAcquireResult.of(
                    IdempotencyAcquireStatus.SUCCESS,
                    touchSlidingWindowIfNeeded(mapper, current, request)
            );
            case DISCARDED -> IdempotencyAcquireResult.of(
                    IdempotencyAcquireStatus.DISCARDED,
                    touchSlidingWindowIfNeeded(mapper, current, request)
            );
            case PROCESSING -> {
                if (isProcessingExpired(current, request.getNow())) {
                    yield IdempotencyAcquireResult.of(
                            IdempotencyAcquireStatus.PROCESSING_EXPIRED,
                            current
                    );
                }
                yield IdempotencyAcquireResult.of(
                        IdempotencyAcquireStatus.PROCESSING_ACTIVE,
                        touchSlidingWindowIfNeeded(mapper, current, request)
                );
            }
            case FAILED -> {
                IdempotencyRecord touched = touchSlidingWindowIfNeeded(mapper, current, request);
                yield IdempotencyAcquireResult.of(
                        touched.isFailureRetryable()
                                ? IdempotencyAcquireStatus.FAILED_RETRYABLE
                                : IdempotencyAcquireStatus.FAILED_FINAL,
                        touched
                );
            }
        };
    }

    /** Recovery 仍使用独立事务和二次 owner/version CAS。 */
    @Override
    public IdempotencyRecoveryResult tryRecover(IdempotencyRecoveryAcquireRequest request) {
        try {
            requireStorage(request.getStorageContext());
            return access.executeInNewTransaction(
                    "idempotency.try-recover",
                    IdempotencyMapper.class,
                    mapper -> tryRecover(mapper, request)
            );
        } catch (Exception failure) {
            return IdempotencyRecoveryResult.providerError(failure);
        }
    }

    private IdempotencyRecoveryResult tryRecover(
            IdempotencyMapper mapper,
            IdempotencyRecoveryAcquireRequest request
    ) {
        IdempotencyStorageContext storage = request.getStorageContext();
        IdempotencyRecord current = selectForUpdate(
                mapper,
                storage,
                request.getNamespace(),
                request.getKey()
        );
        if (current == null) {
            return IdempotencyRecoveryResult.of(IdempotencyRecoveryStatus.NOT_FOUND, null);
        }
        if (current.getRecoveryMode() != IdempotencyRecoveryMode.EXTERNAL_TASK
                || isWindowExpired(current, request.getNow())) {
            return IdempotencyRecoveryResult.of(
                    IdempotencyRecoveryStatus.NOT_RECOVERABLE,
                    current
            );
        }
        if (storageConflict(current, storage)) {
            return IdempotencyRecoveryResult.of(IdempotencyRecoveryStatus.KEY_CONFLICT, current);
        }
        if (request.getExpectedVersion() != null
                && request.getExpectedVersion().longValue() != current.getVersion()) {
            return IdempotencyRecoveryResult.of(
                    IdempotencyRecoveryStatus.STALE_CANDIDATE,
                    current
            );
        }
        if (request.getExpectedOwnerToken() != null
                && !Objects.equals(request.getExpectedOwnerToken(), current.getOwnerToken())) {
            return IdempotencyRecoveryResult.of(
                    IdempotencyRecoveryStatus.STALE_CANDIDATE,
                    current
            );
        }
        if (routeConflict(current.getRouteKey(), request.getRouteKey())
                || hashConflict(current.getRequestHash(), request.getRequestHash())) {
            return IdempotencyRecoveryResult.of(IdempotencyRecoveryStatus.KEY_CONFLICT, current);
        }
        if (current.getStatus() == IdempotencyStatus.SUCCESS) {
            return IdempotencyRecoveryResult.of(IdempotencyRecoveryStatus.SUCCESS, current);
        }
        if (current.getStatus() == IdempotencyStatus.DISCARDED) {
            return IdempotencyRecoveryResult.of(IdempotencyRecoveryStatus.DISCARDED, current);
        }
        if (current.getStatus() == IdempotencyStatus.PROCESSING) {
            if (!isProcessingExpired(current, request.getNow())) {
                return IdempotencyRecoveryResult.of(
                        IdempotencyRecoveryStatus.PROCESSING_ACTIVE,
                        current
                );
            }
            if (!request.isRecoverProcessingTimeout()) {
                return IdempotencyRecoveryResult.of(
                        IdempotencyRecoveryStatus.NOT_RECOVERABLE,
                        current
                );
            }
            return IdempotencyRecoveryResult.acquired(
                    reacquire(mapper, current, request),
                    "PROCESSING_TIMEOUT"
            );
        }
        if (!current.isFailureRetryable() || !request.isRecoverFailed()) {
            return IdempotencyRecoveryResult.of(
                    IdempotencyRecoveryStatus.FAILED_FINAL,
                    current
            );
        }
        return IdempotencyRecoveryResult.acquired(
                reacquire(mapper, current, request),
                current.getFailureCode() == null ? "FAILED_RETRY" : current.getFailureCode()
        );
    }

    /** Tx-B：业务数据和 SUCCESS 使用当前事务中的同一个 MyBatis 资源。 */
    @Override
    public IdempotencyWriteResult markSuccess(IdempotencySuccessRequest request) {
        return complete(request, IdempotencyStatus.SUCCESS, request.getResultPayload());
    }

    /** Tx-B：业务数据和 DISCARDED 使用当前事务中的同一个 MyBatis 资源。 */
    @Override
    public IdempotencyWriteResult markDiscarded(IdempotencyDiscardRequest request) {
        try {
            IdempotencyStorageContext storage = requireStorage(request.getStorageContext());
            return access.executeInCurrentTransaction(
                    "idempotency.mark-discarded",
                    IdempotencyMapper.class,
                    mapper -> complete(
                            mapper,
                            storage,
                            request.getNamespace(),
                            request.getKey(),
                            request.getOwnerToken(),
                            request.getVersion(),
                            IdempotencyStatus.DISCARDED,
                            request.getResultPayload(),
                            request.getMode(),
                            request.getWindowPolicy(),
                            request.getIdempotencyWindow(),
                            request.getRecordRetentionTtl(),
                            request.getNow()
                    )
            );
        } catch (Exception failure) {
            return IdempotencyWriteResult.providerError(failure);
        }
    }

    private IdempotencyWriteResult complete(
            IdempotencySuccessRequest request,
            IdempotencyStatus status,
            String payload
    ) {
        try {
            IdempotencyStorageContext storage = requireStorage(request.getStorageContext());
            return access.executeInCurrentTransaction(
                    "idempotency.mark-success",
                    IdempotencyMapper.class,
                    mapper -> complete(
                            mapper,
                            storage,
                            request.getNamespace(),
                            request.getKey(),
                            request.getOwnerToken(),
                            request.getVersion(),
                            status,
                            payload,
                            request.getMode(),
                            request.getWindowPolicy(),
                            request.getIdempotencyWindow(),
                            request.getRecordRetentionTtl(),
                            request.getNow()
                    )
            );
        } catch (Exception failure) {
            return IdempotencyWriteResult.providerError(failure);
        }
    }

    private IdempotencyWriteResult complete(
            IdempotencyMapper mapper,
            IdempotencyStorageContext storage,
            String namespace,
            String key,
            String ownerToken,
            long version,
            IdempotencyStatus status,
            String payload,
            IdempotencyMode mode,
            IdempotencyWindowPolicy policy,
            Duration window,
            Duration retention,
            Instant now
    ) {
        WindowTimes times = completionWindowTimes(
                mapper, storage, namespace, key, mode, policy, window, retention, now
        );
        IdempotencyRow row = keyRow(storage, namespace, key);
        row.setStatus(status);
        row.setResultPayload(payload);
        row.setCompletedAt(now);
        row.setUpdatedAt(now);
        row.setWindowExpireAt(times.windowExpireAt);
        row.setRetentionExpireAt(times.retentionExpireAt);
        int updated = mapper.complete(tableName, row, ownerToken, version);
        return classifyWrite(mapper, updated, storage, namespace, key, ownerToken, version);
    }

    /** Tx-C：业务事务结束后使用独立事务记录 FAILED。 */
    @Override
    public IdempotencyWriteResult markFailed(IdempotencyFailureRequest request) {
        try {
            IdempotencyStorageContext storage = requireStorage(request.getStorageContext());
            return access.executeInNewTransaction(
                    "idempotency.mark-failed",
                    IdempotencyMapper.class,
                    mapper -> {
                        WindowTimes times = completionWindowTimes(
                                mapper,
                                storage,
                                request.getNamespace(),
                                request.getKey(),
                                request.getMode(),
                                request.getWindowPolicy(),
                                request.getIdempotencyWindow(),
                                request.getRecordRetentionTtl(),
                                request.getNow()
                        );
                        IdempotencyRow row = keyRow(
                                storage,
                                request.getNamespace(),
                                request.getKey()
                        );
                        row.setFailureCode(request.getFailure().getCode());
                        row.setFailureMessage(request.getFailure().getMessage());
                        row.setFailureRetryable(request.getFailure().isRetryable());
                        row.setUpdatedAt(request.getNow());
                        row.setWindowExpireAt(times.windowExpireAt);
                        row.setRetentionExpireAt(times.retentionExpireAt);
                        int updated = mapper.fail(
                                tableName,
                                row,
                                request.getOwnerToken(),
                                request.getVersion()
                        );
                        return classifyWrite(
                                mapper,
                                updated,
                                storage,
                                request.getNamespace(),
                                request.getKey(),
                                request.getOwnerToken(),
                                request.getVersion()
                        );
                    }
            );
        } catch (Exception failure) {
            return IdempotencyWriteResult.providerError(failure);
        }
    }

    @Override
    public Optional<IdempotencyRecord> find(
            IdempotencyStorageContext storageContext,
            String namespace,
            String key
    ) {
        IdempotencyStorageContext storage = requireStorage(storageContext);
        try {
            return access.execute(
                    "idempotency.find",
                    IdempotencyMapper.class,
                    mapper -> Optional.ofNullable(select(mapper, storage, namespace, key))
            );
        } catch (Exception failure) {
            throw new IllegalStateException("query idempotency record failed", failure);
        }
    }

    @Override
    public List<IdempotencyRecoveryCandidate> findRecoveryCandidates(
            IdempotencyRecoveryQuery query
    ) {
        try {
            return access.execute(
                    "idempotency.scan-recovery",
                    IdempotencyMapper.class,
                    mapper -> mapper.findRecoveryCandidates(
                                    tableName,
                                    query.getStoreName(),
                                    query.getScanBucket(),
                                    query.getNamespace(),
                                    query.getNow(),
                                    Math.max(1, query.getLimit())
                            ).stream()
                            .map(IdempotencyRow::toRecoveryCandidate)
                            .toList()
            );
        } catch (Exception failure) {
            throw new IllegalStateException("query recovery candidates failed", failure);
        }
    }

    private boolean tryInsertProcessing(
            IdempotencyMapper mapper,
            IdempotencyAcquireRequest request
    ) {
        IdempotencyStorageContext storage = request.getStorageContext();
        WindowTimes times = initialWindowTimes(request);
        IdempotencyRow row = keyRow(storage, request.getNamespace(), request.getKey());
        row.setRouteKey(request.getRouteKey());
        row.setRequestHash(request.getRequestHash());
        row.setStatus(IdempotencyStatus.PROCESSING);
        row.setOwnerToken(request.getOwnerToken());
        row.setVersion(1L);
        row.setRecoveryMode(request.getRecoveryMode());
        row.setWindowPolicy(request.getWindowPolicy());
        row.setProcessingExpireAt(request.getNow().plus(request.getProcessingTimeout()));
        row.setWindowExpireAt(times.windowExpireAt);
        row.setRetentionExpireAt(times.retentionExpireAt);
        row.setCreatedAt(request.getNow());
        row.setUpdatedAt(request.getNow());
        try {
            return mapper.insertProcessing(tableName, row) == 1;
        } catch (RuntimeException failure) {
            if (MyBatisConstraintViolationDetector.isConstraintViolation(failure)) {
                return false;
            }
            throw failure;
        }
    }

    private IdempotencyRecord restartWindow(
            IdempotencyMapper mapper,
            IdempotencyRecord current,
            IdempotencyAcquireRequest request
    ) {
        WindowTimes times = initialWindowTimes(request);
        IdempotencyRow row = keyRow(
                request.getStorageContext(),
                request.getNamespace(),
                request.getKey()
        );
        row.setRouteKey(request.getRouteKey());
        row.setRequestHash(request.getRequestHash());
        row.setStatus(IdempotencyStatus.PROCESSING);
        row.setOwnerToken(request.getOwnerToken());
        row.setVersion(current.getVersion() + 1L);
        row.setRecoveryMode(request.getRecoveryMode());
        row.setWindowPolicy(request.getWindowPolicy());
        row.setProcessingExpireAt(request.getNow().plus(request.getProcessingTimeout()));
        row.setWindowExpireAt(times.windowExpireAt);
        row.setRetentionExpireAt(times.retentionExpireAt);
        row.setCreatedAt(request.getNow());
        row.setUpdatedAt(request.getNow());
        if (mapper.restartWindow(tableName, row, current.getVersion()) != 1) {
            throw new IllegalStateException("failed to restart expired idempotency window");
        }
        return selectForUpdate(
                mapper,
                request.getStorageContext(),
                request.getNamespace(),
                request.getKey()
        );
    }

    private IdempotencyRecord reacquire(
            IdempotencyMapper mapper,
            IdempotencyRecord previous,
            IdempotencyRecoveryAcquireRequest request
    ) {
        IdempotencyRow row = keyRow(
                request.getStorageContext(),
                previous.getNamespace(),
                previous.getKey()
        );
        row.setStatus(IdempotencyStatus.PROCESSING);
        row.setOwnerToken(request.getNewOwnerToken());
        row.setVersion(previous.getVersion() + 1L);
        row.setProcessingExpireAt(request.getNow().plus(request.getProcessingTimeout()));
        row.setUpdatedAt(request.getNow());
        if (mapper.reacquire(tableName, row, previous.getVersion()) != 1) {
            throw new IllegalStateException("failed to reacquire idempotency record");
        }
        return selectForUpdate(
                mapper,
                request.getStorageContext(),
                previous.getNamespace(),
                previous.getKey()
        );
    }

    private IdempotencyRecord touchSlidingWindowIfNeeded(
            IdempotencyMapper mapper,
            IdempotencyRecord current,
            IdempotencyAcquireRequest request
    ) {
        if (!request.getMode().isWindowed()
                || request.getWindowPolicy() != IdempotencyWindowPolicy.SLIDING_ON_ACCESS
                || request.getIdempotencyWindow() == null) {
            return current;
        }
        Instant windowExpireAt = request.getNow().plus(request.getIdempotencyWindow());
        IdempotencyRow row = keyRow(
                current.storageContext(),
                current.getNamespace(),
                current.getKey()
        );
        row.setWindowExpireAt(windowExpireAt);
        row.setRetentionExpireAt(windowExpireAt.plus(request.getRecordRetentionTtl()));
        row.setUpdatedAt(request.getNow());
        mapper.touchWindow(tableName, row, current.getVersion());
        return selectForUpdate(
                mapper,
                current.storageContext(),
                current.getNamespace(),
                current.getKey()
        );
    }

    private WindowTimes completionWindowTimes(
            IdempotencyMapper mapper,
            IdempotencyStorageContext storage,
            String namespace,
            String key,
            IdempotencyMode mode,
            IdempotencyWindowPolicy policy,
            Duration window,
            Duration retention,
            Instant now
    ) {
        if (!mode.isWindowed()) {
            return WindowTimes.none();
        }
        if (policy == IdempotencyWindowPolicy.SLIDING_ON_ACCESS) {
            Instant windowExpireAt = now.plus(window);
            return new WindowTimes(windowExpireAt, windowExpireAt.plus(retention));
        }
        IdempotencyRecord current = select(mapper, storage, namespace, key);
        return current == null
                ? WindowTimes.none()
                : new WindowTimes(current.getWindowExpireAt(), current.getRetentionExpireAt());
    }

    private WindowTimes initialWindowTimes(IdempotencyAcquireRequest request) {
        if (!request.getMode().isWindowed()) {
            return WindowTimes.none();
        }
        Instant windowExpireAt = request.getNow().plus(request.getIdempotencyWindow());
        return new WindowTimes(
                windowExpireAt,
                windowExpireAt.plus(request.getRecordRetentionTtl())
        );
    }

    private IdempotencyWriteResult classifyWrite(
            IdempotencyMapper mapper,
            int updated,
            IdempotencyStorageContext storage,
            String namespace,
            String key,
            String ownerToken,
            long version
    ) {
        IdempotencyRecord current = select(mapper, storage, namespace, key);
        if (updated == 1) {
            return IdempotencyWriteResult.of(IdempotencyWriteStatus.UPDATED, current);
        }
        if (current == null) {
            return IdempotencyWriteResult.of(IdempotencyWriteStatus.NOT_FOUND, null);
        }
        if (current.getStatus() != IdempotencyStatus.PROCESSING) {
            return IdempotencyWriteResult.of(IdempotencyWriteStatus.ALREADY_FINAL, current);
        }
        if (!Objects.equals(ownerToken, current.getOwnerToken())
                || version != current.getVersion()) {
            return IdempotencyWriteResult.of(IdempotencyWriteStatus.STALE_OWNER, current);
        }
        return IdempotencyWriteResult.providerError(
                new IllegalStateException(
                        "conditional write returned 0 but owner still appears current"
                )
        );
    }

    private IdempotencyRecord selectForUpdate(
            IdempotencyMapper mapper,
            IdempotencyStorageContext storage,
            String namespace,
            String key
    ) {
        IdempotencyRow row = mapper.selectForUpdate(
                tableName,
                storage.getStoreName(),
                namespace,
                key
        );
        return row == null ? null : row.toRecord();
    }

    private IdempotencyRecord select(
            IdempotencyMapper mapper,
            IdempotencyStorageContext storage,
            String namespace,
            String key
    ) {
        IdempotencyRow row = mapper.select(
                tableName,
                storage.getStoreName(),
                namespace,
                key
        );
        return row == null ? null : row.toRecord();
    }

    private IdempotencyRow keyRow(
            IdempotencyStorageContext storage,
            String namespace,
            String key
    ) {
        IdempotencyRow row = new IdempotencyRow();
        row.setStoreName(storage.getStoreName());
        row.setScanBucket(storage.getScanBucket());
        row.setNamespace(namespace);
        row.setIdempotencyKey(key);
        return row;
    }

    private boolean storageConflict(
            IdempotencyRecord current,
            IdempotencyStorageContext requested
    ) {
        return current.getScanBucket() != requested.getScanBucket();
    }

    private boolean isWindowExpired(IdempotencyRecord record, Instant now) {
        return record.getWindowExpireAt() != null && !record.getWindowExpireAt().isAfter(now);
    }

    private boolean isProcessingExpired(IdempotencyRecord record, Instant now) {
        return record.getProcessingExpireAt() != null
                && !record.getProcessingExpireAt().isAfter(now);
    }

    private boolean hashConflict(String oldHash, String newHash) {
        return oldHash != null && !oldHash.isBlank()
                && newHash != null && !newHash.isBlank()
                && !oldHash.equals(newHash);
    }

    private boolean routeConflict(String oldRoute, String newRoute) {
        if ((oldRoute == null || oldRoute.isBlank())
                && (newRoute == null || newRoute.isBlank())) {
            return false;
        }
        return !Objects.equals(normalize(oldRoute), normalize(newRoute));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private IdempotencyStorageContext requireStorage(IdempotencyStorageContext storage) {
        return Objects.requireNonNull(storage, "storageContext must not be null");
    }

    /** WINDOWED 状态迁移需要同时写入的两个过期时间。 */
    private static final class WindowTimes {

        /** 幂等语义窗口到期时间。 */
        private final Instant windowExpireAt;

        /** 记录允许清理的时间。 */
        private final Instant retentionExpireAt;

        private WindowTimes(Instant windowExpireAt, Instant retentionExpireAt) {
            this.windowExpireAt = windowExpireAt;
            this.retentionExpireAt = retentionExpireAt;
        }

        private static WindowTimes none() {
            return new WindowTimes(null, null);
        }
    }
}
