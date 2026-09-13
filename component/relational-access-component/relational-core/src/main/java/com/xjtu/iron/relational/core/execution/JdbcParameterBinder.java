package com.xjtu.iron.relational.core.execution;

import com.xjtu.iron.relational.api.statement.SqlParameter;

import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * 将 Relational API 的位置参数绑定到 JDBC PreparedStatement。
 */
public final class JdbcParameterBinder {

    public void bind(
            PreparedStatement statement,
            List<SqlParameter> parameters
    ) throws SQLException {
        for (int i = 0; i < parameters.size(); i++) {
            bindOne(statement, i + 1, parameters.get(i));
        }
    }

    private void bindOne(
            PreparedStatement statement,
            int index,
            SqlParameter parameter
    ) throws SQLException {
        JDBCType jdbcType = parameter.jdbcType();
        Object value = parameter.value();

        if (value == null && jdbcType != null) {
            statement.setNull(index, jdbcType.getVendorTypeNumber());
            return;
        }
        if (jdbcType != null) {
            statement.setObject(index, value, jdbcType.getVendorTypeNumber());
            return;
        }
        statement.setObject(index, value);
    }
}
