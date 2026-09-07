package com.xjtu.iron.relational.spring.boot.autoconfigure;

import com.xjtu.iron.relational.api.RelationalTemplate;
import com.xjtu.iron.relational.core.DefaultRelationalTemplate;
import com.xjtu.iron.relational.core.connection.SingleDataSourceResolver;
import com.xjtu.iron.relational.core.exception.StandardSqlExceptionTranslator;
import com.xjtu.iron.relational.integration.spring.SpringTransactionAwareConnectionProvider;
import com.xjtu.iron.relational.spi.ConnectionProvider;
import com.xjtu.iron.relational.spi.DataSourceResolver;
import com.xjtu.iron.relational.spi.SqlExceptionTranslator;
import com.xjtu.iron.relational.spi.SqlExecutionContext;
import com.xjtu.iron.relational.spi.SqlExecutionListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * Relational Access v1 的 Spring Boot 自动配置。
 *
 * <p>V1 只处理单 DataSource（或存在唯一 Primary DataSource）的自动装配。多数据源场景应由调用方
 * 提供自定义 {@link DataSourceResolver} / {@link ConnectionProvider}，而不是在本 Starter 内计算分片。</p>
 *
 * <p>本配置不会创建事务。{@link SpringTransactionAwareConnectionProvider} 只会复用 Spring 已经绑定
 * 到当前线程的事务 Connection；事务的 begin/commit/rollback 和传播语义仍由 transaction-component
 * 或 Spring Transaction 在外层负责。</p>
 */
@AutoConfiguration
@ConditionalOnClass({DataSource.class, RelationalTemplate.class})
@ConditionalOnSingleCandidate(DataSource.class)
public class RelationalAccessAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DataSourceResolver relationalDataSourceResolver(DataSource dataSource) {
        return new SingleDataSourceResolver(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public ConnectionProvider relationalConnectionProvider(DataSourceResolver dataSourceResolver) {
        return new SpringTransactionAwareConnectionProvider(dataSourceResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public SqlExceptionTranslator relationalSqlExceptionTranslator() {
        return new StandardSqlExceptionTranslator();
    }

    @Bean
    @ConditionalOnMissingBean(RelationalTemplate.class)
    public RelationalTemplate relationalTemplate(
            ConnectionProvider connectionProvider,
            SqlExceptionTranslator exceptionTranslator,
            ObjectProvider<SqlExecutionListener> listenerProvider
    ) {
        List<SqlExecutionListener> listeners = listenerProvider.orderedStream().toList();
        if (listeners.isEmpty()) {
            return new DefaultRelationalTemplate(connectionProvider, exceptionTranslator);
        }
        return new DefaultRelationalTemplate(
                connectionProvider,
                exceptionTranslator,
                compositeListener(listeners)
        );
    }

    private SqlExecutionListener compositeListener(List<SqlExecutionListener> listeners) {
        return new SqlExecutionListener() {
            @Override
            public void beforeExecute(SqlExecutionContext context) {
                notifyEach(listeners, listener -> listener.beforeExecute(context));
            }

            @Override
            public void afterSuccess(SqlExecutionContext context, Duration elapsed) {
                notifyEach(listeners, listener -> listener.afterSuccess(context, elapsed));
            }

            @Override
            public void afterFailure(SqlExecutionContext context, Duration elapsed, Throwable failure) {
                notifyEach(listeners, listener -> listener.afterFailure(context, elapsed, failure));
            }
        };
    }

    private void notifyEach(
            List<SqlExecutionListener> listeners,
            Consumer<SqlExecutionListener> callback
    ) {
        for (SqlExecutionListener listener : listeners) {
            try {
                callback.accept(listener);
            } catch (RuntimeException ignored) {
                // Observation is a side channel. One listener must not block SQL execution or other listeners.
            }
        }
    }
}
