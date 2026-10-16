package com.xjtu.iron.relational.spring.boot.autoconfigure;

import com.xjtu.iron.relational.mybatis.observation.MyBatisAccessListener;
import com.xjtu.iron.relational.mybatis.resource.DirectMyBatisResourceRegistry;
import com.xjtu.iron.relational.mybatis.resource.DirectMyBatisSqlSessionFactoryCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/** Direct 公共资源装配，仅由显式导入启用，避免组件扫描绕过 Direct 开关。 */
public class RelationalDirectResourceConfiguration {

    @Bean
    @ConditionalOnMissingBean(DirectMyBatisResourceRegistry.class)
    public DirectMyBatisResourceRegistry directMyBatisResourceRegistry(Map<String, DataSource> dataSources,
        ObjectProvider<MyBatisAccessListener> listeners, ObjectProvider<DirectMyBatisSqlSessionFactoryCustomizer> customizers) {
        List<DirectMyBatisSqlSessionFactoryCustomizer> orderedCustomizers = customizers.orderedStream().toList();
        return DirectMyBatisResourceRegistry.fromDataSources(dataSources, MyBatisAccessListener.composite(listeners.orderedStream().toList()),
            (key, factory) -> {
                for (DirectMyBatisSqlSessionFactoryCustomizer customizer : orderedCustomizers) {
                    customizer.customize(key, factory);
                }
            });
    }
}
