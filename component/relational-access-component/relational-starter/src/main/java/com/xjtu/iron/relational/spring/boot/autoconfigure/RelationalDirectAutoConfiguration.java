package com.xjtu.iron.relational.spring.boot.autoconfigure;

import com.xjtu.iron.relational.mybatis.resource.DirectMyBatisResourceRegistry;
import com.xjtu.iron.relational.mybatis.routing.MyBatisAccessResolver;
import com.xjtu.iron.transaction.api.execution.TransactionExecutorResolver;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** 不依赖业务技术组件的 Direct 资源目录及解析器装配。 */
@AutoConfiguration(before = RelationalAccessAutoConfiguration.class,
    afterName = "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration")
@ConditionalOnClass(SqlSessionTemplate.class)
@ConditionalOnProperty(prefix = "xjtu.iron.relational.direct", name = "enabled", havingValue = "true")
@Import(RelationalDirectResourceConfiguration.class)
public class RelationalDirectAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MyBatisAccessResolver.class)
    public MyBatisAccessResolver relationalDirectMyBatisAccessResolver(DirectMyBatisResourceRegistry resources) {
        return resources.getMyBatisAccessResolver();
    }

    @Bean
    @ConditionalOnMissingBean(TransactionExecutorResolver.class)
    public TransactionExecutorResolver relationalDirectTransactionExecutorResolver(DirectMyBatisResourceRegistry resources) {
        return resources.getTransactionExecutorResolver();
    }
}
