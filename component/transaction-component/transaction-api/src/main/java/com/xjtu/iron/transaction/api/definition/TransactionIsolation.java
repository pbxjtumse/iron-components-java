package com.xjtu.iron.transaction.api.definition;

/**
 * 事务隔离级别。
 *
 * <p>组件只表达统一语义；具体数据库和事务管理器是否支持，由 Provider 决定。</p>
 */
public enum TransactionIsolation {

    /** 使用底层事务管理器或数据库的默认隔离级别。 */
    DEFAULT,

    /** 读未提交，可能发生脏读、不可重复读和幻读。 */
    READ_UNCOMMITTED,

    /** 读已提交，避免脏读，但仍可能不可重复读或幻读。 */
    READ_COMMITTED,

    /** 可重复读，同一事务内多次读取保持稳定。 */
    REPEATABLE_READ,

    /** 串行化，以最强隔离换取最低并发度。 */
    SERIALIZABLE
}
