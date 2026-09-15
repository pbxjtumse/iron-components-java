package com.xjtu.iron.idempotent.provider.jdbc.execution;

/**
 * 根据已经解析完成的 dataSourceKey 选择 JDBC 执行管理器。
 *
 * <p>IdempotencyJdbcRouteResolver 只负责“应该去哪一个逻辑 DataSource/物理表”；本接口负责把 dataSourceKey
 * 进一步转换为对应的 JdbcExecutionManager。这样 JdbcIdempotencyRepository 不需要知道 DataSource 注册表，
 * 也不会把 Storage Routing 与事务 Connection 生命周期混在一起。</p>
 */
public interface JdbcExecutionManagerResolver {

    /**
     * @param dataSourceKey null 表示默认 DataSource；非空时必须精确解析，不能静默回退到默认库。
     */
    JdbcExecutionManager resolve(String dataSourceKey);

    /**
     * 是否能够保证所有可能被本 Resolver 返回的 manager 都支持当前业务事务 Connection 参与。
     *
     * <p>Repository capability 是全局声明，因此这里采用保守语义：只要存在不能参与当前事务的路由，
     * 就应返回 false，避免 Core 对 Tx-B 原子性做过强承诺。</p>
     */
    boolean supportsCurrentTransactionParticipation();
}
