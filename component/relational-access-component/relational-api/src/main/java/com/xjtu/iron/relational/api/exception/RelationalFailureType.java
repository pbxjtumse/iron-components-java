package com.xjtu.iron.relational.api.exception;

/**
 * 面向上层 Storage / Retry / Transaction 的稳定关系型失败分类。
 *
 * <p>不同数据库的 SQLState、vendorCode 最终应由 SqlExceptionTranslator 投影到该枚举，
 * 从而避免上层技术组件直接绑定 MySQL/PostgreSQL/Oracle 的具体错误码。</p>
 */
public enum RelationalFailureType {

    /** 获取连接、连接失效、连接恢复失败等数据源连接层错误。 */
    CONNECTION_ERROR,

    /** SqlRoute 指定的数据源无法被当前 DataSourceResolver 解析。 */
    DATA_SOURCE_ROUTING_ERROR,

    /** JDBC Statement 执行超时。 */
    TIMEOUT,

    /** 数据库检测到死锁，当前事务或语句被回滚。 */
    DEADLOCK,

    /** 等待行锁、表锁或元数据锁超时。 */
    LOCK_TIMEOUT,

    /** 唯一键、主键等重复写入冲突。 */
    DUPLICATE_KEY,

    /** 非重复键类的完整性约束失败，例如外键、非空、检查约束。 */
    CONSTRAINT_VIOLATION,

    /** 可串行化隔离级别、乐观并发或事务序列化失败。 */
    SERIALIZATION_FAILURE,

    /** SQL 语法错误、对象不存在、列不存在等结构性 SQL 错误。 */
    SQL_SYNTAX_ERROR,

    /** 数据类型转换、值越界、日期格式等数据内容错误。 */
    DATA_ERROR,

    /** queryOne 期望最多一行，但数据库返回了多行。 */
    NON_UNIQUE_RESULT,

    /** ResultSet 到 Java 对象映射失败，或 RowMapper 返回非法结果。 */
    RESULT_MAPPING_ERROR,

    /** 当前翻译器无法稳定识别的关系型访问错误。 */
    UNKNOWN
}
