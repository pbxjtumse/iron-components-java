package com.xjtu.iron.reliable.task.provider.mybatis.repository;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

/** 从 MyBatis 异常链中识别 SQLState 23 类完整性约束冲突。 */
final class MyBatisConstraintViolationDetector {

    private MyBatisConstraintViolationDetector() {
    }

    static boolean isConstraintViolation(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLIntegrityConstraintViolationException) {
                return true;
            }
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
