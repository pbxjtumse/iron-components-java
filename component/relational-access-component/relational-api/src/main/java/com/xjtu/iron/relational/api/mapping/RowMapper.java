package com.xjtu.iron.relational.api.mapping;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 将当前 ResultSet 行显式映射为领域/存储对象。
 *
 * <p>Relational Access 是 JDBC-based 组件，因此这里有意直接暴露 ResultSet，
 * 不额外包装 IronRow 一类没有实际价值的中间抽象。</p>
 */
@FunctionalInterface
public interface RowMapper<T> {

    T map(ResultSet resultSet) throws SQLException;
}
