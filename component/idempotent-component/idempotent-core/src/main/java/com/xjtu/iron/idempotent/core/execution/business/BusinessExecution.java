package com.xjtu.iron.idempotent.core.execution.business;

import com.xjtu.iron.idempotent.api.execution.IdempotencyContext;
import com.xjtu.iron.idempotent.api.policy.IdempotencyPolicy;
import com.xjtu.iron.idempotent.api.repository.IdempotencyRecord;
import com.xjtu.iron.idempotent.api.repository.IdempotencyRepository;
import com.xjtu.iron.idempotent.core.execution.preparation.IdempotencyExecutionDefinition;
import java.time.Instant;

/** 单次调用的不可变执行信息，只在方法间传递；不是 Spring Bean，也不存入 ThreadLocal 或执行器字段。 */
record BusinessExecution<T>(String key, String routeKey, IdempotencyExecutionDefinition<T> definition,
                            IdempotencyRecord record, IdempotencyContext context, Instant startedAt,
                            boolean lockFallback, boolean recoveryExecution, boolean transactionApplied) {
    IdempotencyPolicy policy() { return definition.policy(); }
    IdempotencyRepository repository() { return definition.repository(); }
}
