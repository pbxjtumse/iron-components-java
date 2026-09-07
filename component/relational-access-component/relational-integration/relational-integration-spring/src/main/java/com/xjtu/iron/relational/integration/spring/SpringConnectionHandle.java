package com.xjtu.iron.relational.integration.spring;

import com.xjtu.iron.relational.spi.ConnectionHandle;
import com.xjtu.iron.relational.spi.ConnectionOwnership;
import org.springframework.jdbc.datasource.DataSourceUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 与 DataSourceUtils 对称释放的 ConnectionHandle。
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
