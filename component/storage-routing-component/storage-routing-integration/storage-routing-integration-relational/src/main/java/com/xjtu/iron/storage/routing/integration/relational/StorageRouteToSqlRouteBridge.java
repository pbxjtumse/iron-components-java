package com.xjtu.iron.storage.routing.integration.relational;

import com.xjtu.iron.relational.api.statement.SqlRoute;
import com.xjtu.iron.relational.api.statement.SqlStatement;
import com.xjtu.iron.storage.routing.api.route.storage.StorageRoute;
import java.util.Objects;

/**
 * Storage Routing 到 Relational Access 的桥接入口。
 *
 * <p>Relational Access 的 SqlRoute 只负责选择 DataSource，不保存表名。因此上层 JDBC Storage 需要同时使用
 * toSqlRoute(route) 和 requireTableName(route)：前者交给 RelationalTemplate 选择连接，后者用于拼装最终 SQL。
 * Direct Adapter 返回物理表名，ShardingSphere-JDBC / Proxy Adapter 返回逻辑表名。</p>
 */
public interface StorageRouteToSqlRouteBridge {

    /**
     * 提取 Relational Access 使用的数据源路由。
     */
    SqlRoute toSqlRoute(StorageRoute route);

    /**
     * 提取上层 Storage 拼 SQL 时使用的执行表名；它可能是直连物理表，也可能是中间件逻辑表。
     */
    String requireTableName(StorageRoute route);

    /**
     * 为已经拼好最终 SQL 的语句附加数据源路由。
     */
    default SqlStatement applyRoute(SqlStatement statement, StorageRoute route) {
        return Objects.requireNonNull(statement, "statement must not be null").withRoute(toSqlRoute(route));
    }
}
