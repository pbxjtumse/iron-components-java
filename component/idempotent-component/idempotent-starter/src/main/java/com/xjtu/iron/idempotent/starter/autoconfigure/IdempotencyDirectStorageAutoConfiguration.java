package com.xjtu.iron.idempotent.starter.autoconfigure;

import com.xjtu.iron.idempotent.core.transaction.IdempotencyTransactionCoordinator;
import com.xjtu.iron.idempotent.integration.storage.routing.StorageRouteAwareIdempotencyTransactionCoordinator;
import com.xjtu.iron.idempotent.integration.storage.routing.StorageRoutingIdempotencyJdbcRouteResolver;
import com.xjtu.iron.idempotent.integration.transaction.DirectStorageResourceRegistry;
import com.xjtu.iron.idempotent.provider.jdbc.execution.JdbcExecutionManagerResolver;
import com.xjtu.iron.idempotent.provider.jdbc.routing.IdempotencyJdbcRouteResolver;
import com.xjtu.iron.idempotent.starter.properties.IdempotencyProperties;
import com.xjtu.iron.storage.routing.api.context.StorageRouteContext;
import com.xjtu.iron.storage.routing.api.resolver.StorageRouteResolver;
import com.xjtu.iron.storage.routing.api.route.ShardRouteInfo;
import com.xjtu.iron.storage.routing.core.mapping.RouteMappingStrategyFactory;
import com.xjtu.iron.storage.routing.starter.autoconfigure.StorageRoutingProperties;
import com.xjtu.iron.transaction.api.execution.TransactionExecutorResolver;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/** 显式 opt-in 的 Direct 本地事务装配；资源注册表同时驱动 Tx-A/B/C。 */
@AutoConfiguration(after = IdempotencyStorageRoutingAutoConfiguration.class, before = IdempotencyAutoConfiguration.class)
@ConditionalOnProperty(prefix = "xjtu.iron.idempotent.jdbc.direct", name = "enabled", havingValue = "true")
public class IdempotencyDirectStorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DirectStorageResourceRegistry.class)
    public DirectStorageResourceRegistry directStorageResourceRegistry(Map<String, DataSource> dataSources) {
        // Bean 名就是 dataSourceKey；多库没有 primary/default 回退。
        return DirectStorageResourceRegistry.fromDataSources(dataSources);
    }

    @Bean
    public TransactionExecutorResolver directTransactionExecutorResolver(DirectStorageResourceRegistry resources) {
        return resources.transactionExecutorResolver();
    }

    @Bean
    public JdbcExecutionManagerResolver directJdbcExecutionManagerResolver(DirectStorageResourceRegistry resources,
            IdempotencyProperties properties, StorageRoutingProperties routingProperties, StorageRouteResolver routeResolver,
            StorageRouteContext routeContext, IdempotencyJdbcRouteResolver jdbcRouteResolver) {
        // 这些依赖缺失时启动失败，不能退回固定表/无路由事务。
        if (!properties.isEnabled() || !properties.getJdbc().isEnabled() || !properties.getTransaction().isEnabled()) {
            throw new IllegalStateException("direct storage requires idempotency, JDBC and transaction to be enabled");
        }
        if (!(jdbcRouteResolver instanceof StorageRoutingIdempotencyJdbcRouteResolver)) {
            throw new IllegalStateException("direct storage requires the storage-routing JDBC route resolver");
        }
        StorageRoutingProperties.Resolver routing = routingProperties.getResolver();
        if (routing.isEnabled()) {
            var mapping = new RouteMappingStrategyFactory(routing.getDataSourcePrefix(), "validation", routing.getDataSourceIndexWidth(), routing.getTableIndexWidth())
                    .create(routing.getTableIndexMode());
            int total = Math.multiplyExact(routing.getDatabaseCount(), routing.getTablesPerDatabase());
            for (int db = 0; db < routing.getDatabaseCount(); db++) {
                resources.require(mapping.map(new ShardRouteInfo(db * routing.getTablesPerDatabase(), db, 0, total)).dataSourceKey());
            }
        }
        return resources.jdbcExecutionManagerResolver();
    }

    @Bean
    public IdempotencyTransactionCoordinator directIdempotencyTransactionCoordinator(StorageRouteContext routes, TransactionExecutorResolver executors) {
        return new StorageRouteAwareIdempotencyTransactionCoordinator(routes, executors);
    }

    @Bean
    public SmartInitializingSingleton directResourceWiringValidator(ObjectProvider<JdbcExecutionManagerResolver> jdbcResolvers,
            ObjectProvider<TransactionExecutorResolver> transactionResolvers, ObjectProvider<IdempotencyTransactionCoordinator> coordinators) {
        return () -> {
            // managed-direct 模式不接受另一份 @Primary Map/Coordinator 抢走调用，避免表面启动成功却用错事务。
            if (jdbcResolvers.stream().count() != 1 || transactionResolvers.stream().count() != 1 || coordinators.stream().count() != 1) {
                throw new IllegalStateException("direct storage requires one registry-owned JDBC resolver, transaction resolver and coordinator; remove competing beans");
            }
        };
    }
}
