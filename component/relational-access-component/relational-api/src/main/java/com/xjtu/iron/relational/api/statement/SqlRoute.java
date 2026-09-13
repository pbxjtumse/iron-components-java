package com.xjtu.iron.relational.api.statement;

import java.util.Objects;

/**
 * 已由上层 Storage / Sharding Adapter 确定的数据源路由结果。
 *
 * <p>{@code dataSourceKey} 表示逻辑数据源标识，通常对应一个 DataSource、一套连接池、
 * 一个数据库实例或一个数据库集群入口。它不是表名。表名属于最终 SQL，例如
 * {@code UPDATE iron_idempotency_record ...}。</p>
 *
 * <p>Relational Access 不计算 shard，也不理解 userId、merchantId 等业务路由键。
 * 上层若启用分库分表，应先计算目标库表：目标库进入 {@code dataSourceKey}，目标表名进入最终 SQL。</p>
 */
public final class SqlRoute {

    private static final SqlRoute DEFAULT_ROUTE = new SqlRoute(null);

    /** 目标数据源键；null 或 blank 表示默认数据源。 */
    private final String dataSourceKey;

    public SqlRoute(String dataSourceKey) {
        this.dataSourceKey = normalize(dataSourceKey);
    }

    public static SqlRoute defaultRoute() {
        return DEFAULT_ROUTE;
    }

    public static SqlRoute of(String dataSourceKey) {
        return new SqlRoute(dataSourceKey);
    }

    public String dataSourceKey() {
        return dataSourceKey;
    }

    public boolean isDefaultRoute() {
        return dataSourceKey == null;
    }

    @Override
    public String toString() {
        return isDefaultRoute()
                ? "SqlRoute{default}"
                : "SqlRoute{dataSourceKey='" + dataSourceKey + "'}";
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof SqlRoute sqlRoute)) {
            return false;
        }
        return Objects.equals(dataSourceKey, sqlRoute.dataSourceKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dataSourceKey);
    }

    private static String normalize(String dataSourceKey) {
        if (dataSourceKey == null || dataSourceKey.isBlank()) {
            return null;
        }
        return dataSourceKey.trim();
    }
}
