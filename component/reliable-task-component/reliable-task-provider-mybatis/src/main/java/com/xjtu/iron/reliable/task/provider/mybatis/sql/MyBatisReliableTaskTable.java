package com.xjtu.iron.reliable.task.provider.mybatis.sql;

import java.util.regex.Pattern;

/** 校验 MyBatis XML 中通过 ${tableName} 展开的可靠任务表名。 */
public final class MyBatisReliableTaskTable {

    /** 未显式配置时采用的可靠任务表名。 */
    public static final String DEFAULT_TABLE_NAME = "iron_reliable_task";

    /** 表名白名单格式，限制为普通表名或单层 schema 限定表名。 */
    private static final Pattern SAFE_TABLE =
            Pattern.compile("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)?");

    private MyBatisReliableTaskTable() {
    }

    public static String validate(String tableName) {
        if (tableName == null || !SAFE_TABLE.matcher(tableName.trim()).matches()) {
            throw new IllegalArgumentException("invalid reliable task table name: " + tableName);
        }
        return tableName.trim();
    }
}
