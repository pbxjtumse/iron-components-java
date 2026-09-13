package com.xjtu.iron.relational.api.statement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 一次确定的 SQL 执行请求。
 *
 * <p>它不是 ORM Entity，也不是 Repository 查询条件。它只描述一条已经被上层 Storage Adapter
 * 或业务 Repository 决定好的最终 SQL，以及这条 SQL 的位置参数、执行选项和数据源路由。</p>
 */
public final class SqlStatement {

    /**
     * 稳定、低基数的逻辑操作名，例如 idempotency.try-acquire；用于指标、日志和链路追踪。
     */
    private final String operationName;

    /**
     * 已经由上层确定的最终 SQL。Relational Access 不解析 SQL，也不拼业务条件。
     */
    private final String sql;

    /**
     * 按 JDBC ? 占位符顺序排列的位置参数。
     */
    private final List<SqlParameter> parameters;

    /**
     * 本次 Statement 级执行选项，例如 queryTimeout、fetchSize、maxRows。
     */
    private final SqlExecutionOptions options;

    /**
     * 已经由上层确定的数据源路由。表名属于 SQL，不属于 route。
     */
    private final SqlRoute route;

    public SqlStatement(
            String operationName,
            String sql,
            List<SqlParameter> parameters,
            SqlExecutionOptions options,
            SqlRoute route
    ) {
        this.operationName = operationName;
        this.sql = sql;
        this.parameters = parameters == null ? List.of() : List.copyOf(parameters);
        this.options = options == null ? SqlExecutionOptions.defaults() : options;
        this.route = route == null ? SqlRoute.defaultRoute() : route;
    }

    /**
     * 最常用的快捷构造：普通参数自动包装为 SqlParameter。
     */
    public static SqlStatement of(String operationName, String sql, Object... parameters) {
        List<SqlParameter> sqlParameters = parameters == null
                ? List.of()
                : Arrays.stream(parameters).map(SqlParameter::of).toList();
        return builder()
                .operationName(operationName)
                .sql(sql)
                .parameters(sqlParameters)
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

    public List<SqlParameter> parameters() {
        return parameters;
    }

    public SqlExecutionOptions options() {
        return options;
    }

    public SqlRoute route() {
        return route;
    }

    public SqlStatement withOptions(SqlExecutionOptions newOptions) {
        return new SqlStatement(operationName, sql, parameters, newOptions, route);
    }

    public SqlStatement withRoute(SqlRoute newRoute) {
        return new SqlStatement(operationName, sql, parameters, options, newRoute);
    }

    @Override
    public String toString() {
        return "SqlStatement{" +
                "operationName='" + operationName + '\'' +
                ", sql='" + sql + '\'' +
                ", parameters=" + parameters +
                ", options=" + options +
                ", route=" + route +
                '}';
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof SqlStatement that)) {
            return false;
        }
        return Objects.equals(operationName, that.operationName)
                && Objects.equals(sql, that.sql)
                && Objects.equals(parameters, that.parameters)
                && Objects.equals(options, that.options)
                && Objects.equals(route, that.route);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationName, sql, parameters, options, route);
    }

    public static final class Builder {

        private String operationName;
        private String sql;
        private final List<SqlParameter> parameters = new ArrayList<>();
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

        public Builder parameters(List<SqlParameter> parameters) {
            this.parameters.clear();
            if (parameters != null) {
                this.parameters.addAll(parameters);
            }
            return this;
        }

        public Builder parameter(SqlParameter parameter) {
            this.parameters.add(Objects.requireNonNull(parameter, "parameter"));
            return this;
        }

        public Builder parameter(Object value) {
            this.parameters.add(SqlParameter.of(value));
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

        public SqlStatement build() {
            return new SqlStatement(operationName, sql, parameters, options, route);
        }
    }
}
