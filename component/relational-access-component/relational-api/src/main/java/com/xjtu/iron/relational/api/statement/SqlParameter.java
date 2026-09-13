package com.xjtu.iron.relational.api.statement;

import java.sql.JDBCType;
import java.util.Objects;

/**
 * 单个 JDBC 位置参数。
 *
 * <p>Relational Access v1 只支持位置参数，不支持命名参数。这样可以保持和 PreparedStatement
 * 一致，避免在基础组件里实现 SQL parser。</p>
 */
public final class SqlParameter {

    /** 参数值，可为 null。 */
    private final Object value;

    /** 显式 JDBC 类型；为 null 时由后续执行实现或驱动推断。 */
    private final JDBCType jdbcType;

    /** 是否为敏感参数；后续日志/观测实现应据此避免明文输出。 */
    private final boolean sensitive;

    public SqlParameter(
            Object value,
            JDBCType jdbcType,
            boolean sensitive
    ) {
        this.value = value;
        this.jdbcType = jdbcType;
        this.sensitive = sensitive;
    }

    public static SqlParameter of(Object value) {
        return new SqlParameter(value, null, false);
    }

    public static SqlParameter of(Object value, JDBCType jdbcType) {
        return new SqlParameter(value, jdbcType, false);
    }

    public static SqlParameter nullOf(JDBCType jdbcType) {
        return new SqlParameter(null, Objects.requireNonNull(jdbcType, "jdbcType"), false);
    }

    public static SqlParameter sensitive(Object value) {
        return new SqlParameter(value, null, true);
    }

    public static SqlParameter sensitive(Object value, JDBCType jdbcType) {
        return new SqlParameter(value, jdbcType, true);
    }

    public Object value() {
        return value;
    }

    public JDBCType jdbcType() {
        return jdbcType;
    }

    public boolean sensitive() {
        return sensitive;
    }

    @Override
    public String toString() {
        return "SqlParameter{" +
                "value=" + (sensitive ? "***" : value) +
                ", jdbcType=" + jdbcType +
                ", sensitive=" + sensitive +
                '}';
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof SqlParameter that)) {
            return false;
        }
        return sensitive == that.sensitive
                && Objects.equals(value, that.value)
                && jdbcType == that.jdbcType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, jdbcType, sensitive);
    }
}
