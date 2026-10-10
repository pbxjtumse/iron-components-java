package com.xjtu.iron.relational.mybatis;

/** 根据物理 dataSourceKey 选择 MyBatis Access。 */
public interface MyBatisAccessResolver {

    /**
     * 解析目标数据库访问运行时。
     *
     * @param dataSourceKey 物理数据源标识；固定单库模式允许为空
     * @return 对应 MyBatis Access
     */
    MyBatisAccess resolve(String dataSourceKey);

    /** 所有可解析资源是否都能参与当前事务。 */
    boolean supportsCurrentTransactionParticipation();
}
