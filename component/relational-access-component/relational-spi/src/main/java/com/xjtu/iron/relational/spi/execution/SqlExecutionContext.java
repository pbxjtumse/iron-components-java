package com.xjtu.iron.relational.spi.execution;

import com.xjtu.iron.relational.api.statement.SqlExecutionOptions;
import com.xjtu.iron.relational.api.statement.SqlRoute;

import java.util.Map;
import java.util.Objects;

/**
 * Relational Core 在一次实际 SQL 执行期间传递给 SPI 的上下文。
 *
 * <p>该对象属于基础设施扩展契约，不是业务上下文，也不应携带 orderId、userId 等
 * 高基数业务标签。它主要服务 ConnectionProvider、DataSourceResolver、SqlExceptionTranslator
 * 和 SqlExecutionListener。</p>
 */
public final class SqlExecutionContext {

    /** 稳定逻辑操作名，例如 idempotency.try-acquire。 */
    private final String operationName;

    /** 实际 JDBC 执行种类。 */
    private final SqlExecutionKind kind;

    /** 最终 SQL。用于异常诊断和必要的低风险观测，不应默认明文打印参数。 */
    private final String sql;

    /** 已确定的数据源路由。 */
    private final SqlRoute route;

    /** Statement 执行选项。 */
    private final SqlExecutionOptions options;

    /** 面向集成扩展的低基数附加属性，例如 batchSize。 */
    private final Map<String, Object> attributes;

    public SqlExecutionContext(
            String operationName,
            SqlExecutionKind kind,
            String sql,
            SqlRoute route,
            SqlExecutionOptions options,
            Map<String, Object> attributes
    ) {
        this.operationName = operationName;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.sql = sql;
        this.route = route == null ? SqlRoute.defaultRoute() : route;
        this.options = options == null ? SqlExecutionOptions.defaults() : options;
        this.attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public String operationName() {
        return operationName;
    }

    public SqlExecutionKind kind() {
        return kind;
    }

    public String sql() {
        return sql;
    }

    public SqlRoute route() {
        return route;
    }

    public SqlExecutionOptions options() {
        return options;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }

    @Override
    public String toString() {
        return "SqlExecutionContext{" +
                "operationName='" + operationName + '\'' +
                ", kind=" + kind +
                ", route=" + route +
                ", options=" + options +
                ", attributes=" + attributes +
                '}';
    }
}
