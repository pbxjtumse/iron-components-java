package com.xjtu.iron.relational.integration.spring;

import com.xjtu.iron.relational.spi.connection.ConnectionHandle;
import com.xjtu.iron.relational.spi.connection.ConnectionOwnership;
import com.xjtu.iron.relational.spi.connection.ConnectionProvider;
import com.xjtu.iron.relational.spi.connection.DataSourceResolver;
import com.xjtu.iron.relational.spi.execution.SqlExecutionContext;
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

 * <p><b>流程阅读编号：D3：获取业务 SQL 连接。</b>编号按 I（幂等）、R（路由）、D（数据访问）分组，不表示所有分支均依次执行。</p>
 * <ul>
 *     <li>1. 先经 D2 确定 DataSource，再调用 DataSourceUtils 获取连接。</li>
 *     <li>2. 判断是否为事务绑定连接，标记 BORROWED 或 OWNED，交给 SpringConnectionHandle 管理释放。</li>
 *     <li>3. 不负责开启事务，也不证明跨库原子性；Tx-B 协调器和幂等 JDBC 管理器必须配置同一资源。</li>
 * </ul>
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

        // D3-1：优先取得 Spring 为该数据源绑定的连接；没有绑定时按正常获取逻辑执行。
        Connection connection = DataSourceUtils.doGetConnection(dataSource);
        boolean transactional = DataSourceUtils.isConnectionTransactional(connection, dataSource);
        ConnectionOwnership ownership = transactional
                ? ConnectionOwnership.BORROWED
                : ConnectionOwnership.OWNED;

        return new SpringConnectionHandle(connection, dataSource, ownership);
    }
}
