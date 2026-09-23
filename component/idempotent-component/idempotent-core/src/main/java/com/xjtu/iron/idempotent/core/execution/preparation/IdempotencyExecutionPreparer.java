package com.xjtu.iron.idempotent.core.execution.preparation;

import com.xjtu.iron.idempotent.api.execution.IdempotencyRequest;
import com.xjtu.iron.idempotent.api.policy.IdempotencyPolicy;
import com.xjtu.iron.idempotent.api.recovery.IdempotencyRecoveryRequest;
import com.xjtu.iron.idempotent.api.repository.IdempotencyRepository;
import com.xjtu.iron.idempotent.api.result.IdempotencyResultPolicies;
import com.xjtu.iron.idempotent.api.result.IdempotencyResultPolicy;
import com.xjtu.iron.idempotent.core.policy.IdempotencyPolicyRegistry;
import com.xjtu.iron.idempotent.core.repository.IdempotencyRepositoryRegistry;

import java.util.Objects;

/** 校验请求，解析策略、Repository 与结果策略。没有状态写入，不计算物理路由。
 * <p><b>流程阅读编号：I2.1：准备配置。</b>编号按 I（幂等）、R（路由）、D（数据访问）分组，不表示所有分支均依次执行。</p>
 * <ul>
 *     <li>1. 检查 key 和逻辑存储上下文，解析本次调用的 Policy 与 Repository。</li>
 *     <li>2. 校验结果策略需要的 payload 能力，返回 ExecutionDefinition。</li>
 *     <li>3. 此阶段没有抢占、事务和 SQL；成功返回不表示已经获得执行权。</li>
 * </ul>
 */
public final class IdempotencyExecutionPreparer {
    private final IdempotencyRepositoryRegistry repositoryRegistry;
    private final IdempotencyPolicyRegistry policyRegistry;

    public IdempotencyExecutionPreparer(IdempotencyRepositoryRegistry repositoryRegistry, IdempotencyPolicyRegistry policyRegistry) {
        this.repositoryRegistry = Objects.requireNonNull(repositoryRegistry, "repositoryRegistry must not be null");
        this.policyRegistry = Objects.requireNonNull(policyRegistry, "policyRegistry must not be null");
    }

    public <T> IdempotencyExecutionDefinition<T> prepareExecution(IdempotencyRequest request, IdempotencyResultPolicy<T> resultPolicy) {
        validateNormalRequest(request);
        return prepare(request.getPolicyName(), request.getPolicy(), resultPolicy);
    }

    public <T> IdempotencyExecutionDefinition<T> prepareRecovery(IdempotencyRecoveryRequest request, IdempotencyResultPolicy<T> resultPolicy) {
        validateRecoveryRequest(request);
        return prepare(request.getPolicyName(), request.getPolicy(), resultPolicy);
    }

    private <T> IdempotencyExecutionDefinition<T> prepare(String policyName, IdempotencyPolicy inlinePolicy,
                                                           IdempotencyResultPolicy<T> resultPolicy) {
        IdempotencyPolicy policy = policyRegistry.resolve(policyName, inlinePolicy);
        policy.validate();
        IdempotencyRepository repository = repositoryRegistry.resolve(policy.getMode(), policy.getRepositoryName());
        IdempotencyResultPolicy<T> resolved = resultPolicy == null ? IdempotencyResultPolicies.none() : resultPolicy;

        // ResultPolicy 若需要持久化返回值，Provider 必须明确支持 result_payload，不能靠调用方假设。
        if (resolved.storesPayload() && !repository.capabilities().isResultPayloadSupported()) {
            throw new IllegalArgumentException("repository " + repository.providerName() + " does not support result payload storage");
        }
        return new IdempotencyExecutionDefinition<>(policy, repository, resolved);
    }

    private void validateNormalRequest(IdempotencyRequest request) {
        if (request == null) throw new IllegalArgumentException("request must not be null");
        validateKey(request.getKey());
        request.storageContext();
    }

    private void validateRecoveryRequest(IdempotencyRecoveryRequest request) {
        if (request == null) throw new IllegalArgumentException("recovery request must not be null");
        validateKey(request.getKey());
        request.storageContext();
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("idempotency key must not be blank");
    }

}
