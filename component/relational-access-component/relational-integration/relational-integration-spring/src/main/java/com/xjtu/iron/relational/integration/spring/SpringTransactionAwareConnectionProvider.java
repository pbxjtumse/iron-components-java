package com.xjtu.iron.relational.integration.spring;

import com.xjtu.iron.relational.spi.ConnectionHandle;
import com.xjtu.iron.relational.spi.ConnectionOwnership;
import com.xjtu.iron.relational.spi.ConnectionProvider;
import com.xjtu.iron.relational.spi.DataSourceResolver;
import com.xjtu.iron.relational.spi.SqlExecutionContext;
import org.springframework.jdbc.datasource.DataSourceUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Spring 本地事务感知的 ConnectionProvider。
 *
 * <p>它不创建事务，只参与已经存在的 Spring transaction-bound Connection：</p>
 * <ul>
 *     <li>当前线程有该 DataSource 的事务 Connection：返回 BORROWED；</li>
 *     <li>当前线程无事务：正常获取 Connection，release 时归还连接池。</li>
 * </ul>
 *
 * <p>REQUIRES_NEW / REQUIRED 等传播语义仍由 transaction-component 或 Spring Transaction
 * 在外层建立，本 Provider 只负责让 RelationalTemplate 真正拿到那个事务绑定 Connection。</p>
 */
public final class SpringTransactionAwareConnectionProvider implements ConnectionProvider {

    private final DataSourceResolver dataSourceResolver;

    public SpringTransactionAwareConnectionProvider(DataSourceResolver dataSourceResolver) {
        this.dataSourceResolver = Objects.requireNonNull(dataSourceResolver, "dataSourceResolver");
    }

    @Override
    public ConnectionHandle acquire(SqlExecutionContext context) throws SQLException {
        DataSource dataSource = Objects.requireNonNull(
                dataSourceResolver.resolve(context),
                "DataSourceResolver.resolve() must not return null"
        );

        Connection connection = DataSourceUtils.doGetConnection(dataSource);
        boolean transactional = DataSourceUtils.isConnectionTransactional(connection, dataSource);
        ConnectionOwnership ownership = transactional
                ? ConnectionOwnership.BORROWED
                : ConnectionOwnership.OWNED;

        return new SpringConnectionHandle(connection, dataSource, ownership);
    }
}
