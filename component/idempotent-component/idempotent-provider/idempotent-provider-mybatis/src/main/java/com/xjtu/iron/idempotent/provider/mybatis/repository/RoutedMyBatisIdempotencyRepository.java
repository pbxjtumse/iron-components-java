package com.xjtu.iron.idempotent.provider.mybatis.repository;

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
import com.xjtu.iron.idempotent.provider.mybatis.routing.IdempotencyPhysicalRoute;
import com.xjtu.iron.idempotent.provider.mybatis.routing.IdempotencyPhysicalRouteResolver;
import com.xjtu.iron.relational.mybatis.MyBatisAccessResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 按物理路由选择公共 MyBatis Access 和幂等表的 Repository 门面。 */
public final class RoutedMyBatisIdempotencyRepository
        implements IdempotencyRepository, IdempotencyRecoveryRepository {

    /** 幂等逻辑上下文到物理目标的解析器。 */
    private final IdempotencyPhysicalRouteResolver routeResolver;

    /** 物理数据源到公共 MyBatis Access 的解析器。 */
    private final MyBatisAccessResolver accessResolver;

    /** 每个物理目标复用的单目标 Repository。 */
    private final ConcurrentMap<IdempotencyPhysicalRoute, MyBatisIdempotencyRepository> delegates
            = new ConcurrentHashMap<>();

    public RoutedMyBatisIdempotencyRepository(
            IdempotencyPhysicalRouteResolver routeResolver,
            MyBatisAccessResolver accessResolver
    ) {
        this.routeResolver = Objects.requireNonNull(routeResolver, "routeResolver must not be null");
        this.accessResolver = Objects.requireNonNull(accessResolver, "accessResolver must not be null");
    }

    @Override
    public String providerName() {
        return MyBatisIdempotencyRepository.PROVIDER_NAME;
    }

    @Override
    public IdempotencyRepositoryCapabilities capabilities() {
        return IdempotencyRepositoryCapabilities.builder()
                .windowedSupported(true)
                .durableSupported(true)
                .resultPayloadSupported(true)
                .businessTransactionParticipationSupported(
                        accessResolver.supportsCurrentTransactionParticipation()
                )
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
            return delegate(
                    request.getStorageContext(),
                    request.getNamespace(),
                    request.getKey()
            ).tryAcquire(request);
        } catch (RuntimeException failure) {
            return IdempotencyAcquireResult.providerError(failure);
        }
    }

    @Override
    public IdempotencyRecoveryResult tryRecover(IdempotencyRecoveryAcquireRequest request) {
        try {
            return delegate(
                    request.getStorageContext(),
                    request.getNamespace(),
                    request.getKey()
            ).tryRecover(request);
        } catch (RuntimeException failure) {
            return IdempotencyRecoveryResult.providerError(failure);
        }
    }

    @Override
    public IdempotencyWriteResult markSuccess(IdempotencySuccessRequest request) {
        try {
            return delegate(
                    request.getStorageContext(),
                    request.getNamespace(),
                    request.getKey()
            ).markSuccess(request);
        } catch (RuntimeException failure) {
            return IdempotencyWriteResult.providerError(failure);
        }
    }

    @Override
    public IdempotencyWriteResult markFailed(IdempotencyFailureRequest request) {
        try {
            return delegate(
                    request.getStorageContext(),
                    request.getNamespace(),
                    request.getKey()
            ).markFailed(request);
        } catch (RuntimeException failure) {
            return IdempotencyWriteResult.providerError(failure);
        }
    }

    @Override
    public IdempotencyWriteResult markDiscarded(IdempotencyDiscardRequest request) {
        try {
            return delegate(
                    request.getStorageContext(),
                    request.getNamespace(),
                    request.getKey()
            ).markDiscarded(request);
        } catch (RuntimeException failure) {
            return IdempotencyWriteResult.providerError(failure);
        }
    }

    @Override
    public Optional<IdempotencyRecord> find(
            IdempotencyStorageContext storageContext,
            String namespace,
            String key
    ) {
        return delegate(storageContext, namespace, key).find(storageContext, namespace, key);
    }

    @Override
    public List<IdempotencyRecoveryCandidate> findRecoveryCandidates(
            IdempotencyRecoveryQuery query
    ) {
        Objects.requireNonNull(query, "query must not be null");
        if (query.getLimit() <= 0) {
            return List.of();
        }
        List<IdempotencyPhysicalRoute> routes = routeResolver.resolveRecoveryRoutes(query);
        if (routes == null || routes.isEmpty()) {
            return List.of();
        }

        List<IdempotencyRecoveryCandidate> result = new ArrayList<>(
                Math.min(query.getLimit(), 128)
        );
        for (IdempotencyPhysicalRoute route : routes.stream().distinct().toList()) {
            int remaining = query.getLimit() - result.size();
            if (remaining <= 0) break;
            IdempotencyRecoveryQuery routedQuery = new IdempotencyRecoveryQuery(
                    query.getStoreName(),
                    query.getNamespace(),
                    query.getScanBucket(),
                    query.getNow(),
                    remaining
            );
            result.addAll(delegate(route).findRecoveryCandidates(routedQuery));
        }
        return result.size() <= query.getLimit()
                ? List.copyOf(result)
                : List.copyOf(result.subList(0, query.getLimit()));
    }

    private MyBatisIdempotencyRepository delegate(
            IdempotencyStorageContext storageContext,
            String namespace,
            String key
    ) {
        Objects.requireNonNull(storageContext, "storageContext must not be null");
        return delegate(routeResolver.resolvePoint(storageContext, namespace, key));
    }

    private MyBatisIdempotencyRepository delegate(IdempotencyPhysicalRoute route) {
        Objects.requireNonNull(route, "route must not be null");
        return delegates.computeIfAbsent(
                route,
                current -> new MyBatisIdempotencyRepository(
                        accessResolver.resolve(current.getDataSourceKey()),
                        current.getTableName()
                )
        );
    }
}
