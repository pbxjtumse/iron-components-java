package com.xjtu.iron.transaction.api.definition;

/**
 * 事务隔离级别。
 *
 * <p>组件只表达统一语义；具体数据库和事务管理器是否支持，由 Provider 决定。</p>
 */
public enum TransactionIsolation {
    DEFAULT,
    READ_UNCOMMITTED,
    READ_COMMITTED,
    REPEATABLE_READ,
    SERIALIZABLE
}
