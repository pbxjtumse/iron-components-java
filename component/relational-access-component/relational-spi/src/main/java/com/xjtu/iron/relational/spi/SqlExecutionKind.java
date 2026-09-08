package com.xjtu.iron.relational.spi;

/**
 * 一次实际关系型访问的执行种类。
 *
 * <p>用于 SPI、指标和日志描述执行行为，不承担 SQL 解析。它描述的是 JDBC 执行路径，
 * 不是业务语义，因此不会拆成 BATCH_INSERT、BATCH_UPDATE、BATCH_DELETE。</p>
 */
public enum SqlExecutionKind {

    /** 查询至多一行数据，对应 RelationalTemplate.queryOne。 */
    QUERY_ONE,

    /** 查询多行数据，对应 RelationalTemplate.queryList。 */
    QUERY_LIST,

    /** 查询单个标量值，对应 RelationalTemplate.queryScalar。 */
    QUERY_SCALAR,

    /** JDBC executeUpdate 路径，可执行 INSERT / UPDATE / DELETE / MERGE / UPSERT 等 DML。 */
    UPDATE,

    /** 通过 Statement.RETURN_GENERATED_KEYS 执行插入并读取数据库生成键。 */
    INSERT_WITH_GENERATED_KEY,

    /** JDBC batch 路径，一条固定 SQL 多组参数，不区分 batch insert / update / delete。 */
    BATCH
}
