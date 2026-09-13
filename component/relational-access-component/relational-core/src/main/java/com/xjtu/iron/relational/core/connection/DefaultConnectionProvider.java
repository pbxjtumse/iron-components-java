package com.xjtu.iron.relational.core.connection;

import com.xjtu.iron.relational.spi.connection.ConnectionHandle;
import com.xjtu.iron.relational.spi.connection.ConnectionProvider;
import com.xjtu.iron.relational.spi.connection.DataSourceResolver;
import com.xjtu.iron.relational.spi.execution.SqlExecutionContext;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * 非事务场景的默认 ConnectionProvider。
 *
 * <p>它先把 SqlRoute 解析为 DataSource，再获取一个由当前 Relational 调用拥有的物理
 * Connection，因此返回 OWNED ConnectionHandle。</p>
 */
public final class DefaultConnectionProvider implements ConnectionProvider {

    private final DataSourceResolver dataSourceResolver;

    public DefaultConnectionProvider(DataSourceResolver dataSourceResolver) {
        this.dataSourceResolver = Objects.requireNonNull(dataSourceResolver, "dataSourceResolver");
    }

    @Override
    public ConnectionHandle acquire(SqlExecutionContext context) throws SQLException {
        DataSource dataSource = Objects.requireNonNull(
                dataSourceResolver.resolve(context),
                "DataSourceResolver.resolve() must not return null"
        );
        Connection connection = dataSource.getConnection();
        return DefaultConnectionHandle.owned(connection);
    }
}
