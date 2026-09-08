package com.xjtu.iron.relational.spi.connection;

import com.xjtu.iron.relational.spi.execution.SqlExecutionContext;

import javax.sql.DataSource;

/**
 * 将已经确定的 dataSourceKey 解析为实际 DataSource。
 *
 * <p>它不是分库分表算法。Sharding Component 应先计算目标库/表；本 SPI 只完成
 * dataSourceKey -> DataSource 的基础设施映射。</p>
 */
public interface DataSourceResolver {

    /**
     * 根据执行上下文中的 SqlRoute 选择目标 DataSource。
     */
    DataSource resolve(SqlExecutionContext context);
}
