package com.xjtu.iron.reliable.task.provider.mybatis.sql;

import com.xjtu.iron.relational.mybatis.support.MyBatisTableNameValidator;

/** 校验 MyBatis XML 中通过 ${tableName} 展开的可靠任务表名。 */
public final class MyBatisReliableTaskTable {

    /** 未显式配置时采用的可靠任务表名。 */
    public static final String DEFAULT_TABLE_NAME = "iron_reliable_task";

    private MyBatisReliableTaskTable() {
    }

    public static String validate(String tableName) {
        return MyBatisTableNameValidator.requireValid(tableName, DEFAULT_TABLE_NAME);
    }
}
