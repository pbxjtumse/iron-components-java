package com.xjtu.iron.idempotent.provider.jdbc.routing;

import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryQuery;
import com.xjtu.iron.idempotent.api.storage.IdempotencyStorageContext;

import java.util.List;

/**
 * 把幂等组件自己的逻辑 Storage 身份解析为 JDBC 最终执行位置。
 *
 * <p>该接口刻意不暴露 StorageRoute/SqlRoute，让 idempotent-provider-jdbc 不反向依赖具体分库分表实现。
 * Storage Routing 集成通过独立 integration 模块实现本接口，固定单库单表场景则使用 FixedIdempotencyJdbcRouteResolver。</p>
 *
 * <p>点查/写入与 Recovery 扫描是两种不同路由问题：前者有幂等 key，集成层还可以复用外层已绑定的业务路由；
 * 后者按 scanBucket 扫描，可能需要扫描多个物理分片，所以返回 List。</p>
 */
public interface IdempotencyJdbcRouteResolver {

    /**
     * 解析单条幂等记录的点查/写入位置。
     */
    IdempotencyJdbcRoute resolvePoint(IdempotencyStorageContext storageContext, String namespace, String idempotencyKey);

    /**
     * 解析本次 Recovery bucket 查询需要访问的物理路由集合。
     *
     * <p>固定单表通常只有一个；真实分库分表场景可以由外部扫描器先绑定分片上下文，
     * integration 再只返回当前分片对应的幂等物理表，避免 idempotent-core 自己管理库表拓扑。</p>
     */
    List<IdempotencyJdbcRoute> resolveRecoveryRoutes(IdempotencyRecoveryQuery query);
}
