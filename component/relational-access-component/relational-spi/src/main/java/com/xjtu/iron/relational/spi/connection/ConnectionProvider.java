package com.xjtu.iron.relational.spi.connection;

import com.xjtu.iron.relational.spi.execution.SqlExecutionContext;

import java.sql.SQLException;

/**
 * 为一次 Relational 执行获取可用 ConnectionHandle。
 *
 * <p>默认实现可从 DataSource 新建 OWNED Connection；事务感知实现可复用当前事务绑定的
 * Connection，并返回 BORROWED ConnectionHandle。</p>
 */
public interface ConnectionProvider {

    /**
     * 根据当前 SQL 执行上下文获取连接句柄。
     */
    ConnectionHandle acquire(SqlExecutionContext context) throws SQLException;
}
