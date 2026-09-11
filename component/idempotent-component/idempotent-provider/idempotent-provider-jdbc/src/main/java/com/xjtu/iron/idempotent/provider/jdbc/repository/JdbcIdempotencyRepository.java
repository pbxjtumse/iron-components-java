package com.xjtu.iron.idempotent.provider.jdbc.repository;

import com.xjtu.iron.idempotent.api.policy.IdempotencyMode;
import com.xjtu.iron.idempotent.api.policy.IdempotencyWindowPolicy;
import com.xjtu.iron.idempotent.api.recovery.IdempotencyRecoveryMode;
import com.xjtu.iron.idempotent.api.repository.IdempotencyRecord;
import com.xjtu.iron.idempotent.api.repository.IdempotencyRepository;
import com.xjtu.iron.idempotent.api.repository.IdempotencyRepositoryCapabilities;
import com.xjtu.iron.idempotent.api.repository.acquire.IdempotencyAcquireRequest;
import com.xjtu.iron.idempotent.api.repository.acquire.IdempotencyAcquireResult;
import com.xjtu.iron.idempotent.api.repository.acquire.IdempotencyAcquireStatus;
import com.xjtu.iron.idempotent.api.repository.recovery.*;
import com.xjtu.iron.idempotent.api.repository.write.*;
import com.xjtu.iron.idempotent.api.state.IdempotencyStatus;
import com.xjtu.iron.idempotent.api.storage.IdempotencyStorageContext;
import com.xjtu.iron.idempotent.provider.jdbc.execution.DataSourceJdbcExecutionManager;
import com.xjtu.iron.idempotent.provider.jdbc.execution.JdbcExecutionManager;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC 幂等状态仓储：DURABLE 的默认实现，也支持 WINDOWED。
 *
 * <p>V2 单表实现已经完整携带 storeName / shardKey / scanBucket，但当前仍写入同一张物理表。
 * 后续真正分库分表时，Core 的 generation/state contract 不需要重新设计。</p>
 *
 * <p>正确性核心仍然是 UNIQUE + 行锁 + ownerToken/version 条件写；DistributedLock 只负责降低热点竞争。</p>
 */
public final class JdbcIdempotencyRepository implements IdempotencyRepository, IdempotencyRecoveryRepository {

    public static final String PROVIDER_NAME = "jdbc";

    /** JDBC 执行适配器，负责普通连接、REQUIRES_NEW、当前事务参与等差异。 */
    private final JdbcExecutionManager jdbc;

    /** 幂等记录表名，构造时已经做白名单校验，避免 SQL 拼接注入。 */
    private final String table;

    public JdbcIdempotencyRepository(DataSource dataSource, String table) {
        this(new DataSourceJdbcExecutionManager(dataSource), table);
    }

    public JdbcIdempotencyRepository(JdbcExecutionManager jdbc, String table) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
        this.table = validateTableName(table);
    }

    @Override
    public String providerName() { return PROVIDER_NAME; }

    @Override
    public IdempotencyRepositoryCapabilities capabilities() {
        return IdempotencyRepositoryCapabilities.builder()
                .windowedSupported(true)
                .durableSupported(true)
                .resultPayloadSupported(true)
                .businessTransactionParticipationSupported(jdbc.supportsCurrentTransactionParticipation())
                .recoveryQuerySupported(true)
                .build();
    }

    /** Tx-A：普通请求原子抢占。只有 ACQUIRED 才真正获得当前 generation。 */
    @Override
    public IdempotencyAcquireResult tryAcquire(IdempotencyAcquireRequest request) {
        try {
            requireStorage(request.getStorageContext());
            return jdbc.inNewTransaction(connection -> tryAcquireInTransaction(connection, request));
        } catch (Exception error) {
            return IdempotencyAcquireResult.providerError(error);
        }
    }

    private IdempotencyAcquireResult tryAcquireInTransaction(Connection connection, IdempotencyAcquireRequest request) throws Exception {
        IdempotencyStorageContext storage = request.getStorageContext();

        // [A1] 首次请求走 INSERT 快路径。唯一键是 storeName + namespace + idempotencyKey。
        if (tryInsertProcessing(connection, request)) {
            return IdempotencyAcquireResult.acquired(selectForUpdate(connection, storage, request.getNamespace(), request.getKey()), false);
        }

        // [A2] Duplicate Key 进入历史状态判定；SELECT FOR UPDATE 避免 Check-Then-Act 竞态。
        IdempotencyRecord current = selectForUpdate(connection, storage, request.getNamespace(), request.getKey());
        if (current == null) {
            return IdempotencyAcquireResult.providerError(new IllegalStateException("idempotency record disappeared after duplicate key"));
        }

        // shardKey / scanBucket 属于持久路由身份，即使 WINDOWED 开启新 generation 也不允许漂移。
        if (storageConflict(current, storage)) {
            return IdempotencyAcquireResult.of(IdempotencyAcquireStatus.KEY_CONFLICT, current);
        }

        // [A3] WINDOWED 语义窗口结束后，同一行 version+1 开启新 generation。
        if (isWindowExpired(current, request.getNow())) {
            return IdempotencyAcquireResult.acquired(restartWindow(connection, current, request), true);
        }

        // [A4] 有效窗口内，同 key 的业务 route / requestHash 必须保持一致。
        if (routeConflict(current.getRouteKey(), request.getRouteKey()) || hashConflict(current.getRequestHash(), request.getRequestHash())) {
            return IdempotencyAcquireResult.of(IdempotencyAcquireStatus.KEY_CONFLICT, current);
        }

        // [A5] 持久状态四态；ACTIVE/EXPIRED/RETRYABLE 等仍是 Repository 派生判定。
        return switch (current.getStatus()) {
            case SUCCESS -> IdempotencyAcquireResult.of(IdempotencyAcquireStatus.SUCCESS,
                    touchSlidingWindowIfNeeded(connection, current, request));
            case DISCARDED -> IdempotencyAcquireResult.of(IdempotencyAcquireStatus.DISCARDED,
                    touchSlidingWindowIfNeeded(connection, current, request));
            case PROCESSING -> {
                if (isProcessingExpired(current, request.getNow())) {
                    yield IdempotencyAcquireResult.of(IdempotencyAcquireStatus.PROCESSING_EXPIRED, current);
                }
                yield IdempotencyAcquireResult.of(IdempotencyAcquireStatus.PROCESSING_ACTIVE,
                        touchSlidingWindowIfNeeded(connection, current, request));
            }
            case FAILED -> {
                IdempotencyRecord touched = touchSlidingWindowIfNeeded(connection, current, request);
                yield IdempotencyAcquireResult.of(touched.isFailureRetryable()
                        ? IdempotencyAcquireStatus.FAILED_RETRYABLE : IdempotencyAcquireStatus.FAILED_FINAL, touched);
            }
        };
    }

    /** 显式 Recovery 二次 CAS。扫描 candidate 本身绝不代表执行许可。 */
    @Override
    public IdempotencyRecoveryResult tryRecover(IdempotencyRecoveryAcquireRequest request) {
        try {
            requireStorage(request.getStorageContext());
            return jdbc.inNewTransaction(connection -> tryRecoverInTransaction(connection, request));
        } catch (Exception error) {
            return IdempotencyRecoveryResult.providerError(error);
        }
    }

    private IdempotencyRecoveryResult tryRecoverInTransaction(Connection connection, IdempotencyRecoveryAcquireRequest request) throws Exception {
        IdempotencyStorageContext storage = request.getStorageContext();
        IdempotencyRecord current = selectForUpdate(connection, storage, request.getNamespace(), request.getKey());
        if (current == null) {
            return IdempotencyRecoveryResult.of(IdempotencyRecoveryStatus.NOT_FOUND, null);
        }

        // Recovery 只处理仍在幂等窗口内、且明确配置 EXTERNAL_TASK 的异常 generation。
        if (current.getRecoveryMode() != IdempotencyRecoveryMode.EXTERNAL_TASK || isWindowExpired(current, request.getNow())) {
            return IdempotencyRecoveryResult.of(IdempotencyRecoveryStatus.NOT_RECOVERABLE, current);
        }
        if (storageConflict(current, storage)) {
            return IdempotencyRecoveryResult.of(IdempotencyRecoveryStatus.KEY_CONFLICT, current);
        }

        // 二次 CAS：candidate 发布到任务队列后，当前 owner/version 可能已经变化。
        if (request.getExpectedVersion() != null && request.getExpectedVersion().longValue() != current.getVersion()) {
            return IdempotencyRecoveryResult.of(IdempotencyRecoveryStatus.STALE_CANDIDATE, current);
        }
        if (request.getExpectedOwnerToken() != null && !Objects.equals(request.getExpectedOwnerToken(), current.getOwnerToken())) {
            return IdempotencyRecoveryResult.of(IdempotencyRecoveryStatus.STALE_CANDIDATE, current);
        }
        if (routeConflict(current.getRouteKey(), request.getRouteKey()) || hashConflict(current.getRequestHash(), request.getRequestHash())) {
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
                return IdempotencyRecoveryResult.of(IdempotencyRecoveryStatus.PROCESSING_ACTIVE, current);
            }
            if (!request.isRecoverProcessingTimeout()) {
                return IdempotencyRecoveryResult.of(IdempotencyRecoveryStatus.NOT_RECOVERABLE, current);
            }

            // 超时 PROCESSING 被允许恢复时，写入新 owner 并 version+1。
            return IdempotencyRecoveryResult.acquired(reacquire(connection, current, request), "PROCESSING_TIMEOUT");
        }

        if (!current.isFailureRetryable() || !request.isRecoverFailed()) {
            return IdempotencyRecoveryResult.of(IdempotencyRecoveryStatus.FAILED_FINAL, current);
        }

        // retryable FAILED 被允许恢复时，同样通过 reacquire 开启下一代 generation。
        return IdempotencyRecoveryResult.acquired(reacquire(connection, current, request),
                current.getFailureCode() == null ? "FAILED_RETRY" : current.getFailureCode());
    }

    /** Tx-B：Business + SUCCESS 同事务完成。 */
    @Override
    public IdempotencyWriteResult markSuccess(IdempotencySuccessRequest request) {
        try {
            IdempotencyStorageContext storage = requireStorage(request.getStorageContext());
            return jdbc.inCurrentTransaction(connection -> {
                WindowTimes times = completionWindowTimes(connection, storage, request.getNamespace(), request.getKey(), request.getMode(),
                        request.getWindowPolicy(), request.getIdempotencyWindow(), request.getRecordRetentionTtl(), request.getNow());
                String sql = "UPDATE " + table
                        + " SET status=?,result_payload=?,failure_code=NULL,failure_message=NULL,failure_retryable=FALSE,"
                        + "processing_expire_at=NULL,completed_at=?,updated_at=?,window_expire_at=?,retention_expire_at=?"
                        + " WHERE store_name=? AND namespace=? AND idempotency_key=? AND status=? AND owner_token=? AND version=?";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, IdempotencyStatus.SUCCESS.name());
                    statement.setString(2, request.getResultPayload());
                    setTimestamp(statement, 3, request.getNow());
                    setTimestamp(statement, 4, request.getNow());
                    setTimestamp(statement, 5, times.windowExpireAt);
                    setTimestamp(statement, 6, times.retentionExpireAt);
                    statement.setString(7, storage.getStoreName());
                    statement.setString(8, request.getNamespace());
                    statement.setString(9, request.getKey());
                    statement.setString(10, IdempotencyStatus.PROCESSING.name());
                    statement.setString(11, request.getOwnerToken());
                    statement.setLong(12, request.getVersion());
                    return classifyWrite(connection, statement.executeUpdate(), storage, request.getNamespace(), request.getKey(),
                            request.getOwnerToken(), request.getVersion());
                }
            });
        } catch (Exception error) {
            return IdempotencyWriteResult.providerError(error);
        }
    }

    /** Tx-C：业务事务回滚以后独立记录 FAILED。 */
    @Override
    public IdempotencyWriteResult markFailed(IdempotencyFailureRequest request) {
        try {
            IdempotencyStorageContext storage = requireStorage(request.getStorageContext());
            return jdbc.inNewTransaction(connection -> {
                WindowTimes times = completionWindowTimes(connection, storage, request.getNamespace(), request.getKey(), request.getMode(),
                        request.getWindowPolicy(), request.getIdempotencyWindow(), request.getRecordRetentionTtl(), request.getNow());
                String sql = "UPDATE " + table
                        + " SET status=?,failure_code=?,failure_message=?,failure_retryable=?,processing_expire_at=NULL,updated_at=?,"
                        + "window_expire_at=?,retention_expire_at=?"
                        + " WHERE store_name=? AND namespace=? AND idempotency_key=? AND status=? AND owner_token=? AND version=?";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, IdempotencyStatus.FAILED.name());
                    statement.setString(2, request.getFailure().getCode());
                    statement.setString(3, request.getFailure().getMessage());
                    statement.setBoolean(4, request.getFailure().isRetryable());
                    setTimestamp(statement, 5, request.getNow());
                    setTimestamp(statement, 6, times.windowExpireAt);
                    setTimestamp(statement, 7, times.retentionExpireAt);
                    statement.setString(8, storage.getStoreName());
                    statement.setString(9, request.getNamespace());
                    statement.setString(10, request.getKey());
                    statement.setString(11, IdempotencyStatus.PROCESSING.name());
                    statement.setString(12, request.getOwnerToken());
                    statement.setLong(13, request.getVersion());
                    return classifyWrite(connection, statement.executeUpdate(), storage, request.getNamespace(), request.getKey(),
                            request.getOwnerToken(), request.getVersion());
                }
            });
        } catch (Exception error) {
            return IdempotencyWriteResult.providerError(error);
        }
    }

    /** Tx-B：明确 DISCARD 与 SUCCESS 一样属于正常终态，应与业务决策同事务提交。 */
    @Override
    public IdempotencyWriteResult markDiscarded(IdempotencyDiscardRequest request) {
        try {
            IdempotencyStorageContext storage = requireStorage(request.getStorageContext());
            return jdbc.inCurrentTransaction(connection -> {
                WindowTimes times = completionWindowTimes(connection, storage, request.getNamespace(), request.getKey(), request.getMode(),
                        request.getWindowPolicy(), request.getIdempotencyWindow(), request.getRecordRetentionTtl(), request.getNow());
                String sql = "UPDATE " + table
                        + " SET status=?,result_payload=?,failure_code=NULL,failure_message=NULL,failure_retryable=FALSE,"
                        + "processing_expire_at=NULL,completed_at=?,updated_at=?,window_expire_at=?,retention_expire_at=?"
                        + " WHERE store_name=? AND namespace=? AND idempotency_key=? AND status=? AND owner_token=? AND version=?";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, IdempotencyStatus.DISCARDED.name());
                    statement.setString(2, request.getResultPayload());
                    setTimestamp(statement, 3, request.getNow());
                    setTimestamp(statement, 4, request.getNow());
                    setTimestamp(statement, 5, times.windowExpireAt);
                    setTimestamp(statement, 6, times.retentionExpireAt);
                    statement.setString(7, storage.getStoreName());
                    statement.setString(8, request.getNamespace());
                    statement.setString(9, request.getKey());
                    statement.setString(10, IdempotencyStatus.PROCESSING.name());
                    statement.setString(11, request.getOwnerToken());
                    statement.setLong(12, request.getVersion());
                    return classifyWrite(connection, statement.executeUpdate(), storage, request.getNamespace(), request.getKey(),
                            request.getOwnerToken(), request.getVersion());
                }
            });
        } catch (Exception error) {
            return IdempotencyWriteResult.providerError(error);
        }
    }

    @Override
    public Optional<IdempotencyRecord> find(IdempotencyStorageContext storageContext, String namespace, String key) {
        IdempotencyStorageContext storage = requireStorage(storageContext);
        try {
            return jdbc.withConnection(connection -> Optional.ofNullable(select(connection, storage, namespace, key, false)));
        } catch (Exception error) {
            throw new IllegalStateException("query idempotency record failed", error);
        }
    }

    /**
     * Reliable Task 分桶扫描候选。这里只返回快照；真正接管仍必须走 tryRecover(expectedOwner, expectedVersion)。
     */
    @Override
    public List<IdempotencyRecoveryCandidate> findRecoveryCandidates(IdempotencyRecoveryQuery query) {
        try {
            return jdbc.withConnection(connection -> queryRecoveryCandidates(connection, query));
        } catch (Exception error) {
            throw new IllegalStateException("query recovery candidates failed", error);
        }
    }

    private List<IdempotencyRecoveryCandidate> queryRecoveryCandidates(Connection connection, IdempotencyRecoveryQuery query) throws SQLException {
        String sql = "SELECT store_name,shard_key,scan_bucket,namespace,idempotency_key,route_key,request_hash,status,owner_token,version,"
                + "processing_expire_at,failure_code FROM " + table
                + " WHERE store_name=? AND scan_bucket=? AND recovery_mode=? AND namespace=?"
                + " AND (window_expire_at IS NULL OR window_expire_at>?)"
                + " AND ((status=? AND processing_expire_at IS NOT NULL AND processing_expire_at<=?)"
                + " OR (status=? AND failure_retryable=TRUE))"
                + " ORDER BY processing_expire_at ASC,id ASC LIMIT ?";

        List<IdempotencyRecoveryCandidate> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setString(index++, query.getStoreName());
            statement.setInt(index++, query.getScanBucket());
            statement.setString(index++, IdempotencyRecoveryMode.EXTERNAL_TASK.name());
            statement.setString(index++, query.getNamespace());
            setTimestamp(statement, index++, query.getNow());
            statement.setString(index++, IdempotencyStatus.PROCESSING.name());
            setTimestamp(statement, index++, query.getNow());
            statement.setString(index++, IdempotencyStatus.FAILED.name());
            statement.setInt(index, Math.max(1, query.getLimit()));

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(new IdempotencyRecoveryCandidate(
                            rs.getString("store_name"), rs.getLong("shard_key"), rs.getInt("scan_bucket"),
                            rs.getString("namespace"), rs.getString("idempotency_key"), rs.getString("route_key"),
                            rs.getString("request_hash"), IdempotencyStatus.valueOf(rs.getString("status")),
                            rs.getString("owner_token"), rs.getLong("version"),
                            toInstant(rs.getTimestamp("processing_expire_at")), rs.getString("failure_code")));
                }
            }
        }
        return result;
    }

    /** 首次请求快路径：通过唯一键插入 PROCESSING。 */
    private boolean tryInsertProcessing(Connection connection, IdempotencyAcquireRequest request) throws SQLException {
        IdempotencyStorageContext storage = request.getStorageContext();
        WindowTimes times = initialWindowTimes(request);
        String sql = "INSERT INTO " + table
                + " (store_name,shard_key,scan_bucket,namespace,idempotency_key,route_key,request_hash,status,owner_token,version,"
                + "result_payload,failure_code,failure_message,failure_retryable,recovery_mode,window_policy,processing_expire_at,"
                + "window_expire_at,retention_expire_at,created_at,updated_at,completed_at)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int i = 1;
            statement.setString(i++, storage.getStoreName());
            statement.setLong(i++, storage.getShardKey());
            statement.setInt(i++, storage.getScanBucket());
            statement.setString(i++, request.getNamespace());
            statement.setString(i++, request.getKey());
            statement.setString(i++, request.getRouteKey());
            statement.setString(i++, request.getRequestHash());
            statement.setString(i++, IdempotencyStatus.PROCESSING.name());
            statement.setString(i++, request.getOwnerToken());
            statement.setLong(i++, 1L);
            statement.setString(i++, null);
            statement.setString(i++, null);
            statement.setString(i++, null);
            statement.setBoolean(i++, false);
            statement.setString(i++, request.getRecoveryMode().name());
            statement.setString(i++, request.getWindowPolicy().name());
            setTimestamp(statement, i++, request.getNow().plus(request.getProcessingTimeout()));
            setTimestamp(statement, i++, times.windowExpireAt);
            setTimestamp(statement, i++, times.retentionExpireAt);
            setTimestamp(statement, i++, request.getNow());
            setTimestamp(statement, i++, request.getNow());
            statement.setTimestamp(i, null);
            statement.executeUpdate();
            return true;
        } catch (SQLException error) {
            if (isConstraintViolation(error)) {
                return false;
            }
            throw error;
        }
    }

    private IdempotencyRecord restartWindow(Connection connection, IdempotencyRecord current, IdempotencyAcquireRequest request) throws SQLException {
        IdempotencyStorageContext storage = request.getStorageContext();
        WindowTimes times = initialWindowTimes(request);

        // WINDOWED 语义窗口已结束：复用同一行，清空历史结果/失败信息，递增 version 开始新逻辑请求。
        String sql = "UPDATE " + table
                + " SET route_key=?,request_hash=?,status=?,owner_token=?,version=?,result_payload=NULL,failure_code=NULL,"
                + "failure_message=NULL,failure_retryable=FALSE,recovery_mode=?,window_policy=?,processing_expire_at=?,"
                + "window_expire_at=?,retention_expire_at=?,created_at=?,updated_at=?,completed_at=NULL"
                + " WHERE store_name=? AND namespace=? AND idempotency_key=? AND version=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int i = 1;
            statement.setString(i++, request.getRouteKey());
            statement.setString(i++, request.getRequestHash());
            statement.setString(i++, IdempotencyStatus.PROCESSING.name());
            statement.setString(i++, request.getOwnerToken());
            statement.setLong(i++, current.getVersion() + 1);
            statement.setString(i++, request.getRecoveryMode().name());
            statement.setString(i++, request.getWindowPolicy().name());
            setTimestamp(statement, i++, request.getNow().plus(request.getProcessingTimeout()));
            setTimestamp(statement, i++, times.windowExpireAt);
            setTimestamp(statement, i++, times.retentionExpireAt);
            setTimestamp(statement, i++, request.getNow());
            setTimestamp(statement, i++, request.getNow());
            statement.setString(i++, storage.getStoreName());
            statement.setString(i++, request.getNamespace());
            statement.setString(i++, request.getKey());
            statement.setLong(i, current.getVersion());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("failed to restart expired idempotency window");
            }
        }
        return selectForUpdate(connection, storage, request.getNamespace(), request.getKey());
    }

    private IdempotencyRecord reacquire(Connection connection, IdempotencyRecord previous,
                                        IdempotencyRecoveryAcquireRequest request) throws SQLException {
        IdempotencyStorageContext storage = request.getStorageContext();

        // Recovery 接管不会新建记录，而是在当前行上替换 ownerToken 并 version+1。
        String sql = "UPDATE " + table
                + " SET status=?,owner_token=?,version=?,processing_expire_at=?,failure_code=NULL,failure_message=NULL,"
                + "failure_retryable=FALSE,result_payload=NULL,completed_at=NULL,updated_at=?"
                + " WHERE store_name=? AND namespace=? AND idempotency_key=? AND version=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, IdempotencyStatus.PROCESSING.name());
            statement.setString(2, request.getNewOwnerToken());
            statement.setLong(3, previous.getVersion() + 1);
            setTimestamp(statement, 4, request.getNow().plus(request.getProcessingTimeout()));
            setTimestamp(statement, 5, request.getNow());
            statement.setString(6, storage.getStoreName());
            statement.setString(7, previous.getNamespace());
            statement.setString(8, previous.getKey());
            statement.setLong(9, previous.getVersion());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("failed to reacquire idempotency record");
            }
        }
        return selectForUpdate(connection, storage, previous.getNamespace(), previous.getKey());
    }

    private IdempotencyRecord touchSlidingWindowIfNeeded(Connection connection, IdempotencyRecord current,
                                                          IdempotencyAcquireRequest request) throws SQLException {
        if (!request.getMode().isWindowed() || request.getWindowPolicy() != IdempotencyWindowPolicy.SLIDING_ON_ACCESS
                || request.getIdempotencyWindow() == null) {
            return current;
        }

        // 滑动窗口只延长窗口/保留时间，不改变 status、owner 或 version。
        WindowTimes next = new WindowTimes(request.getNow().plus(request.getIdempotencyWindow()),
                request.getNow().plus(request.getIdempotencyWindow()).plus(request.getRecordRetentionTtl()));
        String sql = "UPDATE " + table
                + " SET window_expire_at=?,retention_expire_at=?,updated_at=?"
                + " WHERE store_name=? AND namespace=? AND idempotency_key=? AND version=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setTimestamp(statement, 1, next.windowExpireAt);
            setTimestamp(statement, 2, next.retentionExpireAt);
            setTimestamp(statement, 3, request.getNow());
            statement.setString(4, current.getStoreName());
            statement.setString(5, current.getNamespace());
            statement.setString(6, current.getKey());
            statement.setLong(7, current.getVersion());
            statement.executeUpdate();
        }
        return selectForUpdate(connection, current.storageContext(), current.getNamespace(), current.getKey());
    }

    private WindowTimes completionWindowTimes(Connection connection, IdempotencyStorageContext storage, String namespace, String key,
                                              IdempotencyMode mode, IdempotencyWindowPolicy policy, Duration window,
                                              Duration retention, Instant now) throws SQLException {
        if (!mode.isWindowed()) {
            return WindowTimes.none();
        }
        if (policy == IdempotencyWindowPolicy.SLIDING_ON_ACCESS) {
            // SUCCESS/FAILED/DISCARDED 也算一次访问，滑动窗口在完成时重新延长。
            Instant windowAt = now.plus(window);
            return new WindowTimes(windowAt, windowAt.plus(retention));
        }

        // 固定窗口保持首次 acquire 的过期时间，完成状态不重新计算。
        IdempotencyRecord current = select(connection, storage, namespace, key, false);
        return current == null ? WindowTimes.none() : new WindowTimes(current.getWindowExpireAt(), current.getRetentionExpireAt());
    }

    private WindowTimes initialWindowTimes(IdempotencyAcquireRequest request) {
        if (!request.getMode().isWindowed()) {
            return WindowTimes.none();
        }
        Instant windowAt = request.getNow().plus(request.getIdempotencyWindow());
        return new WindowTimes(windowAt, windowAt.plus(request.getRecordRetentionTtl()));
    }

    private IdempotencyWriteResult classifyWrite(Connection connection, int updated, IdempotencyStorageContext storage,
                                                  String namespace, String key, String ownerToken, long version) throws SQLException {
        IdempotencyRecord current = select(connection, storage, namespace, key, false);
        if (updated == 1) {
            return IdempotencyWriteResult.of(IdempotencyWriteStatus.UPDATED, current);
        }

        // 条件更新 0 行需要细分原因，Core 才能区分 stale owner、终态冲突和真正 Provider 异常。
        if (current == null) {
            return IdempotencyWriteResult.of(IdempotencyWriteStatus.NOT_FOUND, null);
        }
        if (current.getStatus() != IdempotencyStatus.PROCESSING) {
            return IdempotencyWriteResult.of(IdempotencyWriteStatus.ALREADY_FINAL, current);
        }
        if (!Objects.equals(ownerToken, current.getOwnerToken()) || version != current.getVersion()) {
            return IdempotencyWriteResult.of(IdempotencyWriteStatus.STALE_OWNER, current);
        }
        return IdempotencyWriteResult.providerError(new IllegalStateException("conditional write returned 0 but owner still appears current"));
    }

    private IdempotencyRecord selectForUpdate(Connection connection, IdempotencyStorageContext storage, String namespace, String key)
            throws SQLException {
        return select(connection, storage, namespace, key, true);
    }

    private IdempotencyRecord select(Connection connection, IdempotencyStorageContext storage, String namespace, String key,
                                     boolean forUpdate) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(selectSql(forUpdate))) {
            statement.setString(1, storage.getStoreName());
            statement.setString(2, namespace);
            statement.setString(3, key);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    private String selectSql(boolean forUpdate) {
        return "SELECT store_name,shard_key,scan_bucket,namespace,idempotency_key,route_key,request_hash,status,owner_token,version,"
                + "result_payload,failure_code,failure_message,failure_retryable,recovery_mode,window_policy,processing_expire_at,"
                + "window_expire_at,retention_expire_at,created_at,updated_at,completed_at FROM " + table
                + " WHERE store_name=? AND namespace=? AND idempotency_key=?" + (forUpdate ? " FOR UPDATE" : "");
    }

    private IdempotencyRecord map(ResultSet rs) throws SQLException {
        return IdempotencyRecord.builder()
                .storeName(rs.getString("store_name"))
                .shardKey(rs.getLong("shard_key"))
                .scanBucket(rs.getInt("scan_bucket"))
                .namespace(rs.getString("namespace"))
                .key(rs.getString("idempotency_key"))
                .routeKey(rs.getString("route_key"))
                .requestHash(rs.getString("request_hash"))
                .status(IdempotencyStatus.valueOf(rs.getString("status")))
                .ownerToken(rs.getString("owner_token"))
                .version(rs.getLong("version"))
                .resultPayload(rs.getString("result_payload"))
                .failureCode(rs.getString("failure_code"))
                .failureMessage(rs.getString("failure_message"))
                .failureRetryable(rs.getBoolean("failure_retryable"))
                .recoveryMode(enumValue(IdempotencyRecoveryMode.class, rs.getString("recovery_mode"), IdempotencyRecoveryMode.NONE))
                .windowPolicy(enumValue(IdempotencyWindowPolicy.class, rs.getString("window_policy"),
                        IdempotencyWindowPolicy.FIXED_FROM_FIRST_ACQUIRE))
                .processingExpireAt(toInstant(rs.getTimestamp("processing_expire_at")))
                .windowExpireAt(toInstant(rs.getTimestamp("window_expire_at")))
                .retentionExpireAt(toInstant(rs.getTimestamp("retention_expire_at")))
                .createdAt(toInstant(rs.getTimestamp("created_at")))
                .updatedAt(toInstant(rs.getTimestamp("updated_at")))
                .completedAt(toInstant(rs.getTimestamp("completed_at")))
                .build();
    }

    private boolean storageConflict(IdempotencyRecord current, IdempotencyStorageContext requested) {
        return current.getShardKey() != requested.getShardKey() || current.getScanBucket() != requested.getScanBucket();
    }

    private boolean isWindowExpired(IdempotencyRecord record, Instant now) {
        return record.getWindowExpireAt() != null && !record.getWindowExpireAt().isAfter(now);
    }

    private boolean isProcessingExpired(IdempotencyRecord record, Instant now) {
        return record.getProcessingExpireAt() != null && !record.getProcessingExpireAt().isAfter(now);
    }

    private boolean hashConflict(String oldHash, String newHash) {
        return oldHash != null && !oldHash.isBlank() && newHash != null && !newHash.isBlank() && !oldHash.equals(newHash);
    }

    private boolean routeConflict(String oldRoute, String newRoute) {
        if ((oldRoute == null || oldRoute.isBlank()) && (newRoute == null || newRoute.isBlank())) {
            return false;
        }
        return !Objects.equals(normalize(oldRoute), normalize(newRoute));
    }

    private String normalize(String value) { return value == null || value.isBlank() ? null : value; }

    private boolean isConstraintViolation(SQLException error) {
        String state = error.getSQLState();
        return state != null && state.startsWith("23");
    }

    private IdempotencyStorageContext requireStorage(IdempotencyStorageContext storage) {
        return Objects.requireNonNull(storage, "storageContext must not be null");
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E defaultValue) {
        return value == null || value.isBlank() ? defaultValue : Enum.valueOf(type, value);
    }

    private static Instant toInstant(Timestamp value) { return value == null ? null : value.toInstant(); }

    private static void setTimestamp(PreparedStatement statement, int index, Instant value) throws SQLException {
        statement.setTimestamp(index, value == null ? null : Timestamp.from(value));
    }

    private static String validateTableName(String value) {
        String table = value == null || value.isBlank() ? "iron_idempotency_record" : value;
        if (!table.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("invalid table name: " + table);
        }
        return table;
    }

    private static final class WindowTimes {
        private final Instant windowExpireAt;
        private final Instant retentionExpireAt;

        private WindowTimes(Instant windowExpireAt, Instant retentionExpireAt) {
            this.windowExpireAt = windowExpireAt;
            this.retentionExpireAt = retentionExpireAt;
        }

        private static WindowTimes none() { return new WindowTimes(null, null); }
    }
}
