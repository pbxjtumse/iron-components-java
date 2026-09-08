package com.xjtu.iron.relational.spi.exception;

import com.xjtu.iron.relational.api.exception.RelationalAccessException;
import com.xjtu.iron.relational.spi.execution.SqlExecutionContext;

import java.sql.SQLException;

/**
 * 将数据库厂商相关 SQLException / SQLState / vendorCode 翻译为稳定的
 * RelationalAccessException + RelationalFailureType。
 */
public interface SqlExceptionTranslator {

    /**
     * 翻译 JDBC 异常。
     *
     * <p>实现可以返回 null，DefaultRelationalTemplate 会兜底转换为 UNKNOWN；
     * 实现自身抛出的 RuntimeException 也会被保护，避免覆盖原始 SQLException。</p>
     */
    RelationalAccessException translate(
            SqlExecutionContext context,
            SQLException exception
    );
}
