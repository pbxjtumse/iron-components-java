package com.xjtu.iron.relational.spring.boot.autoconfigure;

import com.xjtu.iron.relational.api.RelationalTemplate;
import com.xjtu.iron.relational.core.DefaultRelationalTemplate;
import com.xjtu.iron.relational.core.connection.SingleDataSourceResolver;
import com.xjtu.iron.relational.core.exception.StandardSqlExceptionTranslator;
import com.xjtu.iron.relational.integration.spring.SpringTransactionAwareConnectionProvider;
import com.xjtu.iron.relational.spi.connection.ConnectionProvider;
import com.xjtu.iron.relational.spi.connection.DataSourceResolver;
import com.xjtu.iron.relational.spi.exception.SqlExceptionTranslator;
import com.xjtu.iron.relational.spi.execution.SqlExecutionContext;
import com.xjtu.iron.relational.spi.execution.SqlExecutionListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
 * <p>单 DataSource 或唯一 Primary DataSource 场景下，Starter 会自动创建
 * SingleDataSourceResolver -> SpringTransactionAwareConnectionProvider -> RelationalTemplate。</p>
 *
 * <p>多数据源场景下，Starter 不负责创建真实 DataSource，也不负责计算分片。调用方只需要提供
 * 自定义 {@link DataSourceResolver} 或 {@link ConnectionProvider} Bean，本配置仍会继续补齐后续
 * ConnectionProvider / SqlExceptionTranslator / RelationalTemplate。</p>
 *
 * <p>本配置不会创建事务。{@link SpringTransactionAwareConnectionProvider} 只会复用 Spring 已经绑定
 * 到当前线程的事务 Connection；事务的 begin/commit/rollback 和传播语义仍由 transaction-component
 * 或 Spring Transaction 在外层负责。</p>
 */
@AutoConfiguration
@ConditionalOnClass({DataSource.class, RelationalTemplate.class})
public class RelationalAccessAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnSingleCandidate(DataSource.class)
    public DataSourceResolver relationalDataSourceResolver(DataSource dataSource) {
        return new SingleDataSourceResolver(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(DataSourceResolver.class)
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
    @ConditionalOnBean(ConnectionProvider.class)
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
