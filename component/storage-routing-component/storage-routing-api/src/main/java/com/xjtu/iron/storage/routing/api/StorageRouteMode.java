package com.xjtu.iron.storage.routing.api;

/**
 * 存储路由模式。
 *
 * <p>该枚举不代表当前一定已经接入对应中间件，而是用于让上层 Storage 明确理解：
 * 当前路由结果应该如何被解释。</p>
 */
public enum StorageRouteMode {

    /**
     * 应用内显式选择目标 DataSource 和表名。
     *
     * <p>典型场景：StorageRoute.dataSourceKey = order-db-1，tableName = business_order_017，
     * 然后 Relational Access 根据 dataSourceKey 获取真实 DataSource。</p>
     *
     * <p>该模式只表示结果中已经包含物理 dataSourceKey 和 tableName，不决定 10 库每库 10 表、
     * 10 库每库 100 表等拓扑。库表数量由 ShardResolver 决定，表后缀规则由 RouteMappingStrategy /
     * TableIndexMode 决定。</p>
     */
    DIRECT_DATASOURCE,

    /**
     * 统一走 ShardingSphere-JDBC 提供的逻辑 DataSource。
     *
     * <p>此时通常不需要暴露真实物理库，StorageRoute 更关注逻辑表名和 shardKey，
     * 具体 route / rewrite / execute 由 ShardingSphere-JDBC 完成。</p>
     */
    SHARDINGSPHERE_JDBC,

    /**
     * 通过数据库代理层访问，例如 MyCAT / ShardingSphere-Proxy / Vitess vtgate。
     *
     * <p>此时应用侧一般只看到一个逻辑数据库连接，真实分片由代理层处理。</p>
     */
    PROXY
}
