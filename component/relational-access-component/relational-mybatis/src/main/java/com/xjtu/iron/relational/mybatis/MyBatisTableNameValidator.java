package com.xjtu.iron.relational.mybatis;

/** 动态物理表名的统一白名单校验器。 */
public final class MyBatisTableNameValidator {

    private MyBatisTableNameValidator() {
    }

    /** 校验只包含字母、数字和下划线的物理表名。 */
    public static String requireValid(String value, String defaultTableName) {
        String tableName = value == null || value.isBlank() ? defaultTableName : value.trim();
        if (tableName == null || tableName.isBlank() || !tableName.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("invalid physical table name: " + tableName);
        }
        return tableName;
    }
}
