package com.xjtu.iron.relational.api.exception;

/**
 * Relational Access 对外统一异常。
 *
 * <p>异常类型通过 failureType 表达，避免为 deadlock、duplicate-key、timeout 等每种情况
 * 创建大量低价值异常子类。sqlState/vendorCode 保留原始诊断信息，但上层逻辑应优先依赖
 * RelationalFailureType。</p>
 */
public class RelationalAccessException extends RuntimeException {

    private final RelationalFailureType failureType;
    private final String operationName;
    private final String sqlState;
    private final Integer vendorCode;

    public RelationalAccessException(
            RelationalFailureType failureType,
            String operationName,
            String sqlState,
            Integer vendorCode,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.failureType = failureType;
        this.operationName = operationName;
        this.sqlState = sqlState;
        this.vendorCode = vendorCode;
    }

    public RelationalFailureType failureType() {
        return failureType;
    }

    public String operationName() {
        return operationName;
    }

    public String sqlState() {
        return sqlState;
    }

    public Integer vendorCode() {
        return vendorCode;
    }
}
