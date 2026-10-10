package com.xjtu.iron.idempotent.provider.mybatis.routing;

import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryQuery;
import com.xjtu.iron.idempotent.api.storage.IdempotencyStorageContext;

import java.util.List;

/** 将幂等逻辑存储上下文解析为物理数据源和物理表。 */
public interface IdempotencyPhysicalRouteResolver {

    /** 解析点查或条件写入的唯一物理目标。 */
    IdempotencyPhysicalRoute resolvePoint(
            IdempotencyStorageContext storageContext,
            String namespace,
            String idempotencyKey
    );

    /** 解析当前恢复扫描允许访问的物理目标集合。 */
    List<IdempotencyPhysicalRoute> resolveRecoveryRoutes(IdempotencyRecoveryQuery query);
}
