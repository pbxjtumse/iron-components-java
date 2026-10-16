package com.xjtu.iron.idempotent.starter.autoconfigure;

import com.xjtu.iron.idempotent.api.operation.IdempotencyOperations;
import com.xjtu.iron.idempotent.core.operation.DefaultIdempotencyOperations;
import com.xjtu.iron.idempotent.core.policy.IdempotencyPolicyRegistry;
import com.xjtu.iron.idempotent.core.repository.IdempotencyRepositoryRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * V2 低层状态 API 自动装配。
 *
 * <p>与主 {@link IdempotencyAutoConfiguration} 分开，是为了保持原有 Starter 主装配结构稳定；
 * message/task 等组件只要依赖 {@link IdempotencyOperations} 即可，不需要直接访问 Repository。</p>
 */
@AutoConfiguration(after = IdempotencyAutoConfiguration.class)
@ConditionalOnBean({IdempotencyRepositoryRegistry.class, IdempotencyPolicyRegistry.class})
public class IdempotencyOperationsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdempotencyOperations.class)
    public IdempotencyOperations idempotencyOperations(IdempotencyRepositoryRegistry repositoryRegistry,
                                                        IdempotencyPolicyRegistry policyRegistry, Clock clock) {
        return new DefaultIdempotencyOperations(repositoryRegistry, policyRegistry, clock);
    }
}
