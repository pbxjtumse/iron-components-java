package com.xjtu.iron.relational.core.connection;

import com.xjtu.iron.relational.api.exception.RelationalAccessException;
import com.xjtu.iron.relational.api.exception.RelationalFailureType;
import com.xjtu.iron.relational.spi.connection.DataSourceResolver;
import com.xjtu.iron.relational.spi.execution.SqlExecutionContext;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * V1 默认的单数据源解析器。
 *
 * <p>只接受默认路由。若上层已经给出命名 dataSourceKey，则必须换成真正支持多数据源的
 * Resolver，而不是悄悄把所有路由都指向同一个库。</p>
 */
public final class SingleDataSourceResolver implements DataSourceResolver {

    private final DataSource dataSource;

    public SingleDataSourceResolver(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public DataSource resolve(SqlExecutionContext context) {
        if (!context.route().isDefaultRoute()) {
            throw new RelationalAccessException(
                    RelationalFailureType.DATA_SOURCE_ROUTING_ERROR,
                    context.operationName(),
                    null,
                    null,
                    "SingleDataSourceResolver cannot resolve named dataSourceKey="
                            + context.route().dataSourceKey(),
                    null
            );
        }
        return dataSource;
    }
}
