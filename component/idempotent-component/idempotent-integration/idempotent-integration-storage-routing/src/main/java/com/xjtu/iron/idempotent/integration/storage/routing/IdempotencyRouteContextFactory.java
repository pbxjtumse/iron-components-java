package com.xjtu.iron.idempotent.integration.storage.routing;

import com.xjtu.iron.idempotent.api.execution.IdempotencyRequest;
import com.xjtu.iron.idempotent.api.recovery.IdempotencyRecoveryRequest;
import com.xjtu.iron.storage.routing.api.RouteContext;

/**
 * 把幂等请求转换成一次 Storage Routing 输入。
 *
 * <p>接口位于 integration 模块，是因为 idempotent-api/core 不应依赖 Storage Routing，
 * 而业务又需要能够覆盖默认分片键。默认实现使用幂等 key；按 merchantId、tenantId 或复合键
 * 路由的业务可以提供自己的实现。</p>
 */
public interface IdempotencyRouteContextFactory {

    /** 为普通幂等执行创建路由输入。 */
    RouteContext create(IdempotencyRequest request);

    /** 为可靠恢复执行创建路由输入。 */
    RouteContext create(IdempotencyRecoveryRequest request);
}
