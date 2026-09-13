package com.xjtu.iron.relational.spi.connection;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Connection 的资源句柄与所有权边界。
 *
 * <p>它解决的不是“再包装一次 JDBC”，而是明确谁有权关闭物理 Connection。
 * 事务集成返回 BORROWED；普通 DataSource 获取返回 OWNED。</p>
 */
public interface ConnectionHandle extends AutoCloseable {

    /**
     * 返回本次 Relational 执行实际使用的 JDBC Connection。
     */
    Connection connection();

    /**
     * 返回这条 Connection 对当前 Relational 调用的所有权语义。
     */
    ConnectionOwnership ownership();

    /**
     * 释放本次访问对 Connection 的使用权。
     *
     * <p>OWNED 应物理关闭；BORROWED 只能逻辑释放。</p>
     */
    @Override
    void close() throws SQLException;
}
