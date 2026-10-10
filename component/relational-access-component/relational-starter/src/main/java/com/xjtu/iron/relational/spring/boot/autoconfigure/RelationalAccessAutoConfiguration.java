package com.xjtu.iron.relational.spring.boot.autoconfigure;

import com.xjtu.iron.relational.mybatis.FixedMyBatisAccessResolver;
import com.xjtu.iron.relational.mybatis.MyBatisAccess;
import com.xjtu.iron.relational.mybatis.MyBatisAccessInvocation;
import com.xjtu.iron.relational.mybatis.MyBatisAccessListener;
import com.xjtu.iron.relational.mybatis.MyBatisAccessResolver;
import com.xjtu.iron.relational.mybatis.SpringMyBatisAccess;
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
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * 关系型技术表 MyBatis Access 的 Spring Boot 自动配置。
 *
 * <p>本配置复用业务应用已经创建的 DataSource 和 SqlSessionTemplate，不额外创建连接池或
 * SqlSessionFactory。存在 transaction-component 时，Access 同时提供当前事务资源校验和
 * REQUIRES_NEW 能力。</p>
 */
@AutoConfiguration(
        afterName = {
                "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration",
                "com.xjtu.iron.transaction.starter.autoconfigure.TransactionAutoConfiguration"
        }
)
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
        return new DefaultTransactionExecutor(
                new SpringTransactionProvider(new DataSourceTransactionManager(dataSource))
        );
    }

    @Bean
    @ConditionalOnMissingBean(MyBatisAccess.class)
    @ConditionalOnBean(SqlSessionTemplate.class)
    @ConditionalOnSingleCandidate(DataSource.class)
    public MyBatisAccess myBatisAccess(
            DataSource dataSource,
            SqlSessionTemplate sqlSessionTemplate,
            ObjectProvider<TransactionExecutor> transactionExecutorProvider,
            ObjectProvider<MyBatisAccessListener> listenerProvider
    ) {
        return new SpringMyBatisAccess(
                dataSource,
                sqlSessionTemplate,
                transactionExecutorProvider.getIfAvailable(),
                compositeListener(listenerProvider.orderedStream().toList())
        );
    }

    @Bean
    @ConditionalOnBean(MyBatisAccess.class)
    @ConditionalOnMissingBean(MyBatisAccessResolver.class)
    public MyBatisAccessResolver myBatisAccessResolver(MyBatisAccess access) {
        return FixedMyBatisAccessResolver.defaultDataSource(access);
    }

    private MyBatisAccessListener compositeListener(List<MyBatisAccessListener> listeners) {
        if (listeners.isEmpty()) {
            return MyBatisAccessListener.noop();
        }
        return new MyBatisAccessListener() {
            @Override
            public void before(MyBatisAccessInvocation invocation) {
                notifyEach(listeners, listener -> listener.before(invocation));
            }

            @Override
            public void afterSuccess(MyBatisAccessInvocation invocation, Duration elapsed) {
                notifyEach(listeners, listener -> listener.afterSuccess(invocation, elapsed));
            }

            @Override
            public void afterFailure(
                    MyBatisAccessInvocation invocation,
                    Duration elapsed,
                    Throwable failure
            ) {
                notifyEach(listeners, listener -> listener.afterFailure(invocation, elapsed, failure));
            }
        };
    }

    private void notifyEach(
            List<MyBatisAccessListener> listeners,
            Consumer<MyBatisAccessListener> callback
    ) {
        for (MyBatisAccessListener listener : listeners) {
            try {
                callback.accept(listener);
            } catch (RuntimeException ignored) {
                // 观测是旁路能力，不能阻断数据库访问或其他监听器。
            }
        }
    }
}
