package com.xjtu.iron.idempotent.provider.jdbc.repository;

import com.xjtu.iron.idempotent.api.policy.IdempotencyMode;
import com.xjtu.iron.idempotent.api.repository.IdempotencyRecord;
import com.xjtu.iron.idempotent.api.repository.IdempotencyRepository;
import com.xjtu.iron.idempotent.api.repository.IdempotencyRepositoryCapabilities;
import com.xjtu.iron.idempotent.api.repository.acquire.IdempotencyAcquireRequest;
import com.xjtu.iron.idempotent.api.repository.acquire.IdempotencyAcquireResult;
import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryAcquireRequest;
import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryCandidate;
import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryQuery;
import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryRepository;
import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryResult;
import com.xjtu.iron.idempotent.api.repository.write.IdempotencyDiscardRequest;
import com.xjtu.iron.idempotent.api.repository.write.IdempotencyFailureRequest;
import com.xjtu.iron.idempotent.api.repository.write.IdempotencySuccessRequest;
import com.xjtu.iron.idempotent.api.repository.write.IdempotencyWriteResult;
import com.xjtu.iron.idempotent.api.storage.IdempotencyStorageContext;
import com.xjtu.iron.idempotent.provider.jdbc.execution.JdbcExecutionManagerResolver;
import com.xjtu.iron.idempotent.provider.jdbc.routing.IdempotencyJdbcRoute;
import com.xjtu.iron.idempotent.provider.jdbc.routing.IdempotencyJdbcRouteResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 支持 Storage Routing 的 JDBC IdempotencyRepository 门面。
 *
 * <p>该类不重新实现幂等 SQL，也不复制 ownerToken/version、WINDOWED、Recovery 等状态逻辑。
 * 它只在每次调用前把 IdempotencyStorageContext 解析成最终 JDBC 路由，然后复用现有
 * {@link JdbcIdempotencyRepository} 作为“单个 dataSourceKey + 物理表”的原子状态实现。</p>
 *
 * <pre>
 * IdempotencyStorageContext
 *      -> IdempotencyJdbcRouteResolver
 *      -> dataSourceKey + physicalTable
 *      -> JdbcExecutionManagerResolver
 *      -> JdbcIdempotencyRepository
 *      -> UNIQUE / SELECT FOR UPDATE / owner-version CAS
 * </pre>
 *
 * <p>这样可以把“去哪”与“幂等正确性”分开：Storage Routing 可以独立演进，原有 JDBC 状态机无需知道
 * 10 库 100 表、ShardingSphere 或具体 DataSource 注册方式。</p>
 */
public final class RoutedJdbcIdempotencyRepository implements IdempotencyRepository, IdempotencyRecoveryRepository {

    private final IdempotencyJdbcRouteResolver routeResolver;
    private final JdbcExecutionManagerResolver executionManagerResolver;
    private final ConcurrentMap<IdempotencyJdbcRoute, JdbcIdempotencyRepository> delegates = new ConcurrentHashMap<>();

    public RoutedJdbcIdempotencyRepository(
            IdempotencyJdbcRouteResolver routeResolver,
            JdbcExecutionManagerResolver executionManagerResolver
    ) {
        this.routeResolver = Objects.requireNonNull(routeResolver, "routeResolver must not be null");
        this.executionManagerResolver = Objects.requireNonNull(executionManagerResolver, "executionManagerResolver must not be null");
    }

    @Override
    public String providerName() {
        // 对 Core 而言它仍然是 JDBC Provider；routing 是 JDBC Provider 内部的存储位置解析能力。
        return JdbcIdempotencyRepository.PROVIDER_NAME;
    }

    @Override
    public IdempotencyRepositoryCapabilities capabilities() {
        return IdempotencyRepositoryCapabilities.builder()
                .windowedSupported(true)
                .durableSupported(true)
                .resultPayloadSupported(true)
                .businessTransactionParticipationSupported(executionManagerResolver.supportsCurrentTransactionParticipation())
                .recoveryQuerySupported(true)
                .build();
    }

    @Override
    public boolean supports(IdempotencyMode mode) {
        return capabilities().supports(mode);
    }

    @Override
    public IdempotencyAcquireResult tryAcquire(IdempotencyAcquireRequest request) {
        try {
            return delegate(request.getStorageContext(), request.getNamespace(), request.getKey()).tryAcquire(request);
        } catch (RuntimeException error) {
            return IdempotencyAcquireResult.providerError(error);
        }
    }

    @Override
    public IdempotencyRecoveryResult tryRecover(IdempotencyRecoveryAcquireRequest request) {
        try {
            return delegate(request.getStorageContext(), request.getNamespace(), request.getKey()).tryRecover(request);
        } catch (RuntimeException error) {
            return IdempotencyRecoveryResult.providerError(error);
        }
    }

    @Override
    public IdempotencyWriteResult markSuccess(IdempotencySuccessRequest request) {
        try {
            return delegate(request.getStorageContext(), request.getNamespace(), request.getKey()).markSuccess(request);
        } catch (RuntimeException error) {
            return IdempotencyWriteResult.providerError(error);
        }
    }

    @Override
    public IdempotencyWriteResult markFailed(IdempotencyFailureRequest request) {
        try {
            return delegate(request.getStorageContext(), request.getNamespace(), request.getKey()).markFailed(request);
        } catch (RuntimeException error) {
            return IdempotencyWriteResult.providerError(error);
        }
    }

    @Override
    public IdempotencyWriteResult markDiscarded(IdempotencyDiscardRequest request) {
        try {
            return delegate(request.getStorageContext(), request.getNamespace(), request.getKey()).markDiscarded(request);
        } catch (RuntimeException error) {
            return IdempotencyWriteResult.providerError(error);
        }
    }

    @Override
    public Optional<IdempotencyRecord> find(IdempotencyStorageContext storageContext, String namespace, String key) {
        return delegate(storageContext, namespace, key).find(storageContext, namespace, key);
    }

    @Override
    public List<IdempotencyRecoveryCandidate> findRecoveryCandidates(IdempotencyRecoveryQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        if (query.getLimit() <= 0) return List.of();

        List<IdempotencyJdbcRoute> routes = routeResolver.resolveRecoveryRoutes(query);
        if (routes == null || routes.isEmpty()) return List.of();

        List<IdempotencyRecoveryCandidate> result = new ArrayList<>(Math.min(query.getLimit(), 128));
        for (IdempotencyJdbcRoute route : routes.stream().distinct().toList()) {
            int remaining = query.getLimit() - result.size();
            if (remaining <= 0) break;

            IdempotencyRecoveryQuery routedQuery = new IdempotencyRecoveryQuery(
                    query.getStoreName(), query.getNamespace(), query.getScanBucket(), query.getNow(), remaining);
            result.addAll(delegate(route).findRecoveryCandidates(routedQuery));
        }
        return result.size() <= query.getLimit() ? List.copyOf(result) : List.copyOf(result.subList(0, query.getLimit()));
    }

    private JdbcIdempotencyRepository delegate(IdempotencyStorageContext storageContext, String namespace, String key) {
        Objects.requireNonNull(storageContext, "storageContext must not be null");
        IdempotencyJdbcRoute route = routeResolver.resolvePoint(storageContext, namespace, key);
        return delegate(route);
    }

    private JdbcIdempotencyRepository delegate(IdempotencyJdbcRoute route) {
        Objects.requireNonNull(route, "route must not be null");
        return delegates.computeIfAbsent(route, current -> new JdbcIdempotencyRepository(
                executionManagerResolver.resolve(current.dataSourceKey()), current.tableName()));
    }
}
