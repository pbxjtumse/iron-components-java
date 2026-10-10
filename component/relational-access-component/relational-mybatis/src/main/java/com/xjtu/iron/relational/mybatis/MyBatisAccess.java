package com.xjtu.iron.relational.mybatis;

/**
 * 关系型技术表共用的 MyBatis Mapper 执行入口。
 *
 * <p>组件 Repository 仍拥有自己的 Mapper 和 SQL；本接口只统一 Mapper 获取、事务资源校验、
 * REQUIRES_NEW 执行以及观测生命周期。</p>
 */
public interface MyBatisAccess {

    /** 在当前调用上下文中执行一次 Mapper 操作。 */
    <M, T> T execute(String operationName, Class<M> mapperType, MyBatisMapperWork<M, T> work) throws Exception;

    /** 在当前已经存在的本地事务中执行，并校验该事务绑定了本 Access 的 DataSource。 */
    <M, T> T executeInCurrentTransaction(
            String operationName,
            Class<M> mapperType,
            MyBatisMapperWork<M, T> work
    ) throws Exception;

    /** 在 REQUIRES_NEW 本地事务中执行 Mapper 操作。 */
    <M, T> T executeInNewTransaction(
            String operationName,
            Class<M> mapperType,
            MyBatisMapperWork<M, T> work
    ) throws Exception;

    /** 是否能够保证 Mapper 参与当前 Spring 本地事务。 */
    boolean supportsCurrentTransactionParticipation();
}
