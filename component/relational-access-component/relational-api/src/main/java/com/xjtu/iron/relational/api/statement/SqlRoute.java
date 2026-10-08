package com.xjtu.iron.relational.api.statement;

import java.util.Objects;

/**
 * 已由上层 Storage / Sharding Adapter 确定的数据源路由结果{@code dataSourceKey}
 *
 * <p><b>流程阅读编号：数据模型 D0：SQL 数据源选择。</b>编号按 I（幂等）、R（路由）、D（数据访问）分组，不表示所有分支均依次执行。</p>
 * <ul>
 *     <li>1. 上层通过 bridge 或显式配置构造路由，随 SqlStatement 传入 Template。</li>
 *     <li>2. 命名路由的 dataSourceKey 由数据源 resolver 解释</li>
 * </ul>
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
