package com.xjtu.iron.reliable.task.starter.properties;

/** Reliable Task 持久化 Repository 的启动期实现选择。 */
public enum ReliableTaskProviderType {

    /** 使用 RelationalTemplate 驱动的原生 JDBC Provider。 */
    JDBC,

    /** 使用 MyBatis Mapper 驱动的 Provider。 */
    MYBATIS
}
