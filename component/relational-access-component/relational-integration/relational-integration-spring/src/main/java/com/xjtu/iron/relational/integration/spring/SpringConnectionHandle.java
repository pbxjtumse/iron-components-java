package com.xjtu.iron.relational.integration.spring;

import com.xjtu.iron.relational.spi.connection.ConnectionHandle;
import com.xjtu.iron.relational.spi.connection.ConnectionOwnership;
import org.springframework.jdbc.datasource.DataSourceUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 与 DataSourceUtils 对称释放的 ConnectionHandle。

 * <p><b>流程阅读编号：D4：释放业务 SQL 连接句柄。</b>编号按 I（幂等）、R（路由）、D（数据访问）分组，不表示所有分支均依次执行。</p>
 * <ul>
 *     <li>1. Template 的 try-with-resources 调用 close；AtomicBoolean 防止重复释放。</li>
 *     <li>2. 委托 DataSourceUtils 释放，事务绑定连接由 Spring 事务生命周期管理。</li>
 *     <li>3. 句柄关闭不等于业务事务提交，也不应直接替代事务管理器关闭借用的连接。</li>
 * </ul>
 */
final class SpringConnectionHandle implements ConnectionHandle {

    private final Connection connection;
    private final DataSource dataSource;
    private final ConnectionOwnership ownership;
    private final AtomicBoolean released = new AtomicBoolean(false);

    SpringConnectionHandle(
            Connection connection,
            DataSource dataSource,
            ConnectionOwnership ownership
    ) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.ownership = Objects.requireNonNull(ownership, "ownership");
    }

    @Override
    public Connection connection() {
        return connection;
    }

    @Override
    public ConnectionOwnership ownership() {
        return ownership;
    }

    @Override
    public void close() throws SQLException {
        if (!released.compareAndSet(false, true)) {
            return;
        }
        DataSourceUtils.doReleaseConnection(connection, dataSource);
    }
}
