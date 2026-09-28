package com.xjtu.iron.idempotent.core.execution.business;

import com.xjtu.iron.idempotent.api.execution.IdempotencyContext;
import com.xjtu.iron.idempotent.api.policy.IdempotencyPolicy;
import com.xjtu.iron.idempotent.api.repository.IdempotencyRecord;
import com.xjtu.iron.idempotent.api.repository.IdempotencyRepository;
import com.xjtu.iron.idempotent.core.execution.preparation.IdempotencyExecutionDefinition;
import java.time.Instant;

/** 单次调用的不可变执行信息，只在方法间传递；不是 Spring Bean，也不存入 ThreadLocal 或执行器字段。
 * <p><b>流程阅读编号：I6.0：单次执行信息。</b>编号按 I（幂等）、R（路由）、D（数据访问）分组，不表示所有分支均依次执行。</p>
 * <ul>
 *     <li>1. 由业务执行器在获得记录后创建，沿内部方法参数传递。</li>
 *     <li>2. record/context 携带本代执行身份；definition 携带策略和 Repository；标记用于响应与观测。</li>
 *     <li>3. 不可变指字段引用固定，不代表引用的 Repository 深度不可变；不存入单例字段或 ThreadLocal。</li>
 * </ul>
 */
record BusinessExecution<T>(String key, String routeKey, IdempotencyExecutionDefinition<T> definition,
                            IdempotencyRecord record, IdempotencyContext context, Instant startedAt,
                            boolean lockFallback, boolean recoveryExecution, boolean transactionApplied) {
    IdempotencyPolicy policy() { return definition.policy(); }
    IdempotencyRepository repository() { return definition.repository(); }
}
