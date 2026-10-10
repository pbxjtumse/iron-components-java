package com.xjtu.iron.relational.mybatis;

import java.sql.SQLException;

/** 从 MyBatis/Spring 异常链中识别 SQLState 23 类完整性约束冲突。 */
public final class MyBatisConstraintViolationDetector {

    private MyBatisConstraintViolationDetector() {
    }

    /** 判断异常链中是否存在数据库完整性约束冲突。 */
    public static boolean isConstraintViolation(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                String sqlState = sqlException.getSQLState();
                if (sqlState != null && sqlState.startsWith("23")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
