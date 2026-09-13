package com.xjtu.iron.relational.core.connection;

import com.xjtu.iron.relational.spi.connection.ConnectionHandle;
import com.xjtu.iron.relational.spi.connection.ConnectionOwnership;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 默认 ConnectionHandle。
 *
 * <p>close() 幂等：OWNED 第一次 close 时关闭物理 Connection；BORROWED 永不关闭物理连接。</p>
 */
public final class DefaultConnectionHandle implements ConnectionHandle {

    private final Connection connection;
    private final ConnectionOwnership ownership;
    private final AtomicBoolean released = new AtomicBoolean(false);

    private DefaultConnectionHandle(
            Connection connection,
            ConnectionOwnership ownership
    ) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.ownership = Objects.requireNonNull(ownership, "ownership");
    }

    public static DefaultConnectionHandle owned(Connection connection) {
        return new DefaultConnectionHandle(connection, ConnectionOwnership.OWNED);
    }

    public static DefaultConnectionHandle borrowed(Connection connection) {
        return new DefaultConnectionHandle(connection, ConnectionOwnership.BORROWED);
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
        if (ownership == ConnectionOwnership.OWNED) {
            connection.close();
        }
    }
}
