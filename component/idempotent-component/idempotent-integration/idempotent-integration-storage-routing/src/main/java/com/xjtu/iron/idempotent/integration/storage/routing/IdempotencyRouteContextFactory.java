package com.xjtu.iron.idempotent.integration.storage.routing;

import com.xjtu.iron.idempotent.api.execution.IdempotencyRequest;
import com.xjtu.iron.idempotent.api.recovery.IdempotencyRecoveryRequest;
import com.xjtu.iron.storage.routing.api.RouteContext;

/**
 * 把幂等请求转换成一次 Storage Routing 输入。
 *
 * <p>接口位于 integration 模块，是因为 idempotent-api/core 不应依赖 Storage Routing。默认实现使用幂等 key，
 * 只在当前没有业务 StorageRoute 时调用。需要按 merchantId、tenantId 或复合键与业务表同分片时，业务层应先打开
 * StorageRouteScope；不要把业务分片模型重新塞进 IdempotencyRequest。</p>
 */
public interface IdempotencyRouteContextFactory {

    /** 为普通幂等执行创建路由输入。 */
    RouteContext create(IdempotencyRequest request);

    /** 为可靠恢复执行创建路由输入。 */
    RouteContext create(IdempotencyRecoveryRequest request);
}
