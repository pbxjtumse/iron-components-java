package com.xjtu.iron.relational.spring.boot.autoconfigure;

import com.xjtu.iron.relational.mybatis.routing.FixedMyBatisAccessResolver;
import com.xjtu.iron.relational.mybatis.access.MyBatisAccess;
import com.xjtu.iron.relational.mybatis.observation.MyBatisAccessListener;
import com.xjtu.iron.relational.mybatis.routing.MyBatisAccessResolver;
import com.xjtu.iron.relational.mybatis.access.SpringMyBatisAccess;
import com.xjtu.iron.transaction.api.execution.TransactionExecutor;
import com.xjtu.iron.transaction.core.executor.DefaultTransactionExecutor;
import com.xjtu.iron.transaction.provider.spring.transaction.SpringTransactionProvider;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

/**
 * 关系型技术表 MyBatis Access 的 Spring Boot 自动配置。
 *
 * <p>本配置复用业务应用已经创建的 DataSource 和 SqlSessionTemplate，不额外创建连接池或
 * SqlSessionFactory。存在 transaction-component 时，Access 同时提供当前事务资源校验和
 * REQUIRES_NEW 能力。</p>
 */
@AutoConfiguration(after = RelationalDirectAutoConfiguration.class,
    afterName = {"org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration",
    "com.xjtu.iron.transaction.starter.autoconfigure.TransactionAutoConfiguration"})
@ConditionalOnClass({SqlSessionTemplate.class, MyBatisAccess.class})
public class RelationalAccessAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MyBatisAccessListener.class)
    public MyBatisAccessListener myBatisAccessListener() {
        return MyBatisAccessListener.noop();
    }

    @Bean
    @ConditionalOnMissingBean(TransactionExecutor.class)
    @ConditionalOnSingleCandidate(DataSource.class)
    public TransactionExecutor myBatisTransactionExecutor(DataSource dataSource) {
        return new DefaultTransactionExecutor(new SpringTransactionProvider(new DataSourceTransactionManager(dataSource)));
    }

    @Bean
    @ConditionalOnMissingBean(MyBatisAccess.class)
    @ConditionalOnBean(SqlSessionTemplate.class)
    @ConditionalOnSingleCandidate(DataSource.class)
    public MyBatisAccess myBatisAccess(DataSource dataSource, SqlSessionTemplate sqlSessionTemplate,
        ObjectProvider<TransactionExecutor> transactionExecutorProvider, ObjectProvider<MyBatisAccessListener> listenerProvider) {
        return new SpringMyBatisAccess(dataSource, sqlSessionTemplate, transactionExecutorProvider.getIfAvailable(),
            MyBatisAccessListener.composite(listenerProvider.orderedStream().toList()));
    }

    @Bean
    @ConditionalOnBean(MyBatisAccess.class)
    @ConditionalOnMissingBean(MyBatisAccessResolver.class)
    public MyBatisAccessResolver myBatisAccessResolver(MyBatisAccess access) {
        return FixedMyBatisAccessResolver.defaultDataSource(access);
    }
}
