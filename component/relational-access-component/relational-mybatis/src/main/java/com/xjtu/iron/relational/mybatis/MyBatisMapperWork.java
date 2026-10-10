package com.xjtu.iron.relational.mybatis;

/**
 * 使用一个类型安全 MyBatis Mapper 完成一次技术表访问。
 *
 * @param <M> Mapper 接口类型
 * @param <T> 执行结果类型
 */
@FunctionalInterface
public interface MyBatisMapperWork<M, T> {

    /**
     * 执行 Mapper 调用。
     *
     * @param mapper 当前 MyBatis 运行时创建的 Mapper
     * @return 执行结果
     * @throws Exception Mapper 调用或上层转换失败
     */
    T execute(M mapper) throws Exception;
}
