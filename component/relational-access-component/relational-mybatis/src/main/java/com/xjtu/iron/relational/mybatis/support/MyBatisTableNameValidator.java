package com.xjtu.iron.relational.mybatis.support;

import java.util.regex.Pattern;

/** 动态物理表名的统一白名单校验器。 */
public final class MyBatisTableNameValidator {

    /** 普通表名或一层 schema 限定表名，禁止引号、空白、注释和 SQL 运算符。 */
    private static final Pattern SAFE_TABLE_NAME = Pattern.compile("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)?");

    private MyBatisTableNameValidator() {
    }

    /** 校验普通表名或一层 schema 限定表名。 */
    public static String requireValid(String value, String defaultTableName) {
        String tableName = value == null || value.isBlank() ? defaultTableName : value.trim();
        if (tableName == null || tableName.isBlank() || !SAFE_TABLE_NAME.matcher(tableName).matches()) {
            throw new IllegalArgumentException("invalid physical table name: " + tableName);
        }
        return tableName;
    }
}
