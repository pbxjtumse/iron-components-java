package com.xjtu.iron.idempotent.core.execution.preparation;

import com.xjtu.iron.idempotent.api.policy.IdempotencyPolicy;
import com.xjtu.iron.idempotent.api.repository.IdempotencyRepository;
import com.xjtu.iron.idempotent.api.result.IdempotencyResultPolicy;

import java.util.Objects;

/**
 * API 输入进入 Core 后冻结得到的一次不可变执行定义。
 *
 * <p>它与并行组件中的 TaskDefinition 思路一致：
 * Request/Policy/ResultPolicy/Repository 先解析完成，主执行流程不再到处读取可选配置。</p>
 */
public final class IdempotencyExecutionDefinition<T> {

    private final IdempotencyPolicy policy;
    private final IdempotencyRepository repository;
    private final IdempotencyResultPolicy<T> resultPolicy;

    IdempotencyExecutionDefinition(IdempotencyPolicy policy, IdempotencyRepository repository, IdempotencyResultPolicy<T> resultPolicy) {
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.resultPolicy = Objects.requireNonNull(resultPolicy, "resultPolicy must not be null");
    }

    public IdempotencyPolicy policy() { return policy; }
    public IdempotencyRepository repository() { return repository; }
    public IdempotencyResultPolicy<T> resultPolicy() { return resultPolicy; }
}
