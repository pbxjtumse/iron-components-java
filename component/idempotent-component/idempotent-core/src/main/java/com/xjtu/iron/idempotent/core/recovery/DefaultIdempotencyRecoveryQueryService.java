package com.xjtu.iron.idempotent.core.recovery;

import com.xjtu.iron.idempotent.api.policy.IdempotencyPolicy;
import com.xjtu.iron.idempotent.api.recovery.IdempotencyRecoveryQueryService;
import com.xjtu.iron.idempotent.api.repository.IdempotencyRepository;
import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryCandidate;
import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryQuery;
import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryRepository;
import com.xjtu.iron.idempotent.core.policy.IdempotencyPolicyRegistry;
import com.xjtu.iron.idempotent.core.repository.IdempotencyRepositoryRegistry;

import java.util.List;
import java.util.Objects;

/**
 * 默认 Recovery candidate 查询服务。
 *
 * <p>它只负责“发现可能需要恢复的快照”，绝不修改 owner/version，也绝不直接执行业务。扫描结果必须交给
 * IdempotencyExecutor.recover(...)，由 tryRecover(expectedOwner, expectedVersion) 再做一次实时原子校验。</p>
 */
public final class DefaultIdempotencyRecoveryQueryService implements IdempotencyRecoveryQueryService {

    private final IdempotencyRepositoryRegistry repositoryRegistry;
    private final IdempotencyPolicyRegistry policyRegistry;

    public DefaultIdempotencyRecoveryQueryService(IdempotencyRepositoryRegistry repositoryRegistry,
                                                   IdempotencyPolicyRegistry policyRegistry) {
        this.repositoryRegistry = Objects.requireNonNull(repositoryRegistry, "repositoryRegistry must not be null");
        this.policyRegistry = Objects.requireNonNull(policyRegistry, "policyRegistry must not be null");
    }

    /**
     * 使用 policyName 查询 candidate，确保扫描链路和正常 execute() 使用同一 namespace / mode / Repository。
     *
     * <p>V2 调用方只负责指定 storeName + scanBucket + now + limit；namespace 仍从 Policy 获取，
     * 避免任务配置和正常执行配置漂移。</p>
     */
    @Override
    public List<IdempotencyRecoveryCandidate> findCandidates(String policyName, IdempotencyRecoveryQuery query) {
        Objects.requireNonNull(policyName, "policyName must not be null");
        Objects.requireNonNull(query, "query must not be null");

        IdempotencyPolicy policy = policyRegistry.resolve(policyName, null);
        IdempotencyRepository repository = repositoryRegistry.resolve(policy.getMode(), policy.getRepositoryName());

        IdempotencyRecoveryQuery effectiveQuery = new IdempotencyRecoveryQuery(
                query.getStoreName(), policy.getNamespace(), query.getScanBucket(), query.getNow(), query.getLimit());
        return query(repository, effectiveQuery);
    }

    private List<IdempotencyRecoveryCandidate> query(IdempotencyRepository repository, IdempotencyRecoveryQuery query) {
        if (!repository.capabilities().isRecoveryQuerySupported()
                || !(repository instanceof IdempotencyRecoveryRepository recoveryRepository)) {
            return List.of();
        }
        return recoveryRepository.findRecoveryCandidates(query);
    }
}
