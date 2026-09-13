package com.xjtu.iron.relational.api.statement;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 同一 SQL、不同参数组的批量执行请求。
 *
 * <p>它只表达 JDBC batch 机制：一条固定 SQL，多组位置参数。批量插入、批量更新、批量删除
 * 都可以使用该模型，真正语义由 sql 字段决定。</p>
 */
public final class BatchSqlStatement {

    /** 稳定逻辑操作名，例如 outbox.batch-insert。 */
    private final String operationName;

    /** 批量执行的固定 SQL。 */
    private final String sql;

    /** 每个元素代表一次 addBatch 所需的完整位置参数列表。 */
    private final List<List<SqlParameter>> batches;

    /** Statement 执行选项。 */
    private final SqlExecutionOptions options;

    /** 已由上层确定的数据源路由。 */
    private final SqlRoute route;

    public BatchSqlStatement(
            String operationName,
            String sql,
            List<List<SqlParameter>> batches,
            SqlExecutionOptions options,
            SqlRoute route
    ) {
        this.operationName = operationName;
        this.sql = sql;
        this.batches = copyBatches(batches);
        this.options = options == null ? SqlExecutionOptions.defaults() : options;
        this.route = route == null ? SqlRoute.defaultRoute() : route;
    }

    public static BatchSqlStatement of(
            String operationName,
            String sql,
            List<List<SqlParameter>> batches
    ) {
        return builder()
                .operationName(operationName)
                .sql(sql)
                .batches(batches)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String operationName() {
        return operationName;
    }

    public String sql() {
        return sql;
    }

    public List<List<SqlParameter>> batches() {
        return batches;
    }

    public SqlExecutionOptions options() {
        return options;
    }

    public SqlRoute route() {
        return route;
    }

    public BatchSqlStatement withOptions(SqlExecutionOptions newOptions) {
        return new BatchSqlStatement(operationName, sql, batches, newOptions, route);
    }

    public BatchSqlStatement withRoute(SqlRoute newRoute) {
        return new BatchSqlStatement(operationName, sql, batches, options, newRoute);
    }

    @Override
    public String toString() {
        return "BatchSqlStatement{" +
                "operationName='" + operationName + '\'' +
                ", sql='" + sql + '\'' +
                ", batchSize=" + batches.size() +
                ", options=" + options +
                ", route=" + route +
                '}';
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof BatchSqlStatement that)) {
            return false;
        }
        return Objects.equals(operationName, that.operationName)
                && Objects.equals(sql, that.sql)
                && Objects.equals(batches, that.batches)
                && Objects.equals(options, that.options)
                && Objects.equals(route, that.route);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationName, sql, batches, options, route);
    }

    private static List<List<SqlParameter>> copyBatches(List<List<SqlParameter>> batches) {
        if (batches == null) {
            return List.of();
        }
        return batches.stream()
                .map(batch -> batch == null ? null : List.copyOf(batch))
                .toList();
    }

    public static final class Builder {

        private String operationName;
        private String sql;
        private final List<List<SqlParameter>> batches = new ArrayList<>();
        private SqlExecutionOptions options = SqlExecutionOptions.defaults();
        private SqlRoute route = SqlRoute.defaultRoute();

        private Builder() {
        }

        public Builder operationName(String operationName) {
            this.operationName = operationName;
            return this;
        }

        public Builder sql(String sql) {
            this.sql = sql;
            return this;
        }

        public Builder batches(List<List<SqlParameter>> batches) {
            this.batches.clear();
            if (batches != null) {
                this.batches.addAll(copyBatches(batches));
            }
            return this;
        }

        public Builder addBatch(List<SqlParameter> parameters) {
            this.batches.add(parameters == null ? null : List.copyOf(parameters));
            return this;
        }

        public Builder options(SqlExecutionOptions options) {
            this.options = options == null ? SqlExecutionOptions.defaults() : options;
            return this;
        }

        public Builder route(SqlRoute route) {
            this.route = route == null ? SqlRoute.defaultRoute() : route;
            return this;
        }

        public BatchSqlStatement build() {
            return new BatchSqlStatement(operationName, sql, batches, options, route);
        }
    }
}
