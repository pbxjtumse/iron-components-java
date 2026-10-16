package com.xjtu.iron.transaction.starter.autoconfigure;

import com.xjtu.iron.transaction.api.event.TransactionEventListener;
import com.xjtu.iron.transaction.api.execution.TransactionExecutor;
import com.xjtu.iron.transaction.core.executor.DefaultTransactionExecutor;
import com.xjtu.iron.transaction.core.id.UuidTransactionExecutionIdGenerator;
import com.xjtu.iron.transaction.provider.spring.transaction.SpringTransactionProvider;
import com.xjtu.iron.transaction.spi.id.TransactionExecutionIdGenerator;
import com.xjtu.iron.transaction.spi.provider.TransactionProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

/**
 * 事务组件一期 Spring Boot 自动配置。
 *
 * <p>一期只接受单一候选 PlatformTransactionManager；多 TransactionManager 选择和路由放到二期。</p>
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
})
@ConditionalOnClass(PlatformTransactionManager.class)
@ConditionalOnProperty(prefix = "iron.transaction", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnSingleCandidate(PlatformTransactionManager.class)
public class TransactionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TransactionProvider.class)
    public TransactionProvider transactionProvider(PlatformTransactionManager transactionManager) {
        // ORM 与数据库差异由 Spring 具体 TransactionManager 处理，组件只适配统一接口。
        return new SpringTransactionProvider(transactionManager);
    }

    @Bean
    @ConditionalOnMissingBean(TransactionExecutionIdGenerator.class)
    public TransactionExecutionIdGenerator transactionExecutionIdGenerator() {
        // 默认使用本地 UUID。工程接入 foundation-id 后，只需提供同类型 Bean 即可替换。
        return new UuidTransactionExecutionIdGenerator();
    }

    @Bean
    @ConditionalOnMissingBean(TransactionExecutor.class)
    public TransactionExecutor transactionExecutor(
            TransactionProvider provider,
            TransactionExecutionIdGenerator executionIdGenerator,
            ObjectProvider<TransactionEventListener> listeners) {
        // 监听器按 Spring Order 收集，统一创建显式 TransactionExecutor 门面。
        List<TransactionEventListener> orderedListeners = listeners.orderedStream().toList();
        return new DefaultTransactionExecutor(provider, executionIdGenerator, orderedListeners);
    }
}
