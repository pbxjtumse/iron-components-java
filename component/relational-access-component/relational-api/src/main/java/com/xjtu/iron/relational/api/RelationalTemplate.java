package com.xjtu.iron.relational.api;

import com.xjtu.iron.relational.api.mapping.RowMapper;
import com.xjtu.iron.relational.api.result.BatchResult;
import com.xjtu.iron.relational.api.result.GeneratedKey;
import com.xjtu.iron.relational.api.result.UpdateResult;
import com.xjtu.iron.relational.api.statement.BatchSqlStatement;
import com.xjtu.iron.relational.api.statement.SqlStatement;

import java.util.List;
import java.util.Optional;

/**
 * 关系型数据库访问的稳定门面。
 *
 * <p>该接口主要供技术组件的 JDBC Storage Adapter 使用，例如：
 * JdbcIdempotencyStorage、JdbcOutboxStorage、JdbcTaskStorage。</p>
 *
 * <p>调用方负责提供明确 SQL、参数和结果映射；Relational Access 负责后续统一的
 * Connection 获取、Statement 生命周期、参数绑定、异常翻译和可观测执行。</p>
 *
 * <p>本接口不承担 ORM、业务 Repository、事务边界、分库分表算法和自动重试。
 * 因此这里不会提供 updateById、insertSelective、updateByUniqueKey 等 DataMapper/ORM
 * 风格方法；这些能力应由上层 Storage、业务 MyBatis Mapper 或未来专门的业务 Repository 实现。</p>
 */
public interface RelationalTemplate {

    /**
     * 查询至多一行数据。
     *
     * <p>0 行返回 Optional.empty()；1 行由 RowMapper 映射；超过 1 行应由实现层
     * 转换为 NON_UNIQUE_RESULT 类型的 RelationalAccessException。</p>
     */
    <T> Optional<T> queryOne(SqlStatement statement, RowMapper<T> rowMapper);

    /**
     * 查询多行数据并逐行映射。
     */
    <T> List<T> queryList(SqlStatement statement, RowMapper<T> rowMapper);

    /**
     * 查询单个标量值，例如 COUNT(*)、MAX(id) 或单列状态值。
     */
    <T> Optional<T> queryScalar(SqlStatement statement, Class<T> requiredType);

    /**
     * 执行 JDBC executeUpdate() 路径。
     *
     * <p>这里的方法名叫 update，但并不只代表 SQL UPDATE 语句。最终执行什么动作，完全由
     * {@link SqlStatement#sql()} 中传入的最终 SQL 决定：可以是 INSERT、UPDATE、DELETE、
     * MERGE、UPSERT 或数据库方言支持的其他 DML。RelationalTemplate 不解析 SQL，也不理解
     * 表名、主键、唯一键和 selective 更新语义，只返回 JDBC 的受影响行数。</p>
     */
    UpdateResult update(SqlStatement statement);

    /**
     * 执行插入并读取数据库生成键。
     *
     * <p>这是独立方法的原因不是“面向业务新增 insert 接口”，而是 JDBC 需要通过
     * Statement.RETURN_GENERATED_KEYS 使用特殊的 prepareStatement 路径。</p>
     */
    <K> GeneratedKey<K> insertAndReturnKey(SqlStatement statement, Class<K> keyType);

    /**
     * 对同一 SQL 的多组参数执行 JDBC batch。
     *
     * <p>这里同样不区分 batch insert / batch update / batch delete。最终批量执行什么动作，
     * 由 {@link BatchSqlStatement#sql()} 决定。Relational Access 只负责循环 bind 参数、
     * addBatch，并调用 executeBatch。</p>
     */
    BatchResult batchUpdate(BatchSqlStatement statement);

    /**
     * batchUpdate 的语义化别名。
     *
     * <p>保留 batchUpdate 是为了贴近 JDBC 术语；增加 batch 是为了表达它不仅能做批量 UPDATE，
     * 也可以做批量 INSERT、DELETE、UPSERT。</p>
     */
    default BatchResult batch(BatchSqlStatement statement) {
        return batchUpdate(statement);
    }
}
