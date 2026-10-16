package com.xjtu.iron.relational.mybatis.resource;

import org.mybatis.spring.SqlSessionFactoryBean;

/** Direct 每库 MyBatis 工厂的定制入口，在默认数据源和 Mapper XML 设置后执行。 */
@FunctionalInterface
public interface DirectMyBatisSqlSessionFactoryCustomizer {

    /** 可设置插件、TypeHandler 或 Mapper 资源；最终工厂仍必须使用当前资源的数据源。 */
    void customize(String dataSourceKey, SqlSessionFactoryBean factoryBean) throws Exception;
}
