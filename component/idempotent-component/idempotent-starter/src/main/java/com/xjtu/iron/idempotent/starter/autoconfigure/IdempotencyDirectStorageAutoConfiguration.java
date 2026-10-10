package com.xjtu.iron.idempotent.starter.autoconfigure;

import com.xjtu.iron.idempotent.core.transaction.IdempotencyTransactionCoordinator;
import com.xjtu.iron.idempotent.integration.storage.routing.StorageRouteAwareIdempotencyTransactionCoordinator;
import com.xjtu.iron.idempotent.integration.storage.routing.StorageRoutingIdempotencyPhysicalRouteResolver;
import com.xjtu.iron.idempotent.provider.mybatis.routing.IdempotencyPhysicalRouteResolver;
import com.xjtu.iron.relational.mybatis.DirectMyBatisResourceRegistry;
import com.xjtu.iron.relational.mybatis.MyBatisAccessResolver;
import com.xjtu.iron.idempotent.starter.properties.IdempotencyProperties;
import com.xjtu.iron.storage.routing.api.context.StorageRouteContext;
import com.xjtu.iron.storage.routing.api.resolver.StorageRouteResolver;
import com.xjtu.iron.storage.routing.api.route.ShardRouteInfo;
import com.xjtu.iron.storage.routing.api.route.StorageRouteMode;
import com.xjtu.iron.storage.routing.core.mapping.RouteMappingStrategyFactory;
import com.xjtu.iron.storage.routing.starter.autoconfigure.StorageRoutingProperties;
import com.xjtu.iron.transaction.api.execution.TransactionExecutorResolver;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 显式 opt-in 的 Direct 本地事务装配；资源注册表同时驱动 Tx-A/B/C。 */
@AutoConfiguration(after = IdempotencyStorageRoutingAutoConfiguration.class, before = IdempotencyAutoConfiguration.class)
@ConditionalOnProperty(prefix = "xjtu.iron.idempotent.mybatis.direct", name = "enabled", havingValue = "true")
public class IdempotencyDirectStorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DirectMyBatisResourceRegistry.class)
    public DirectMyBatisResourceRegistry directMyBatisResourceRegistry(Map<String, DataSource> dataSources) {
        // Bean 名就是 dataSourceKey；多库没有 primary/default 回退。
        return DirectMyBatisResourceRegistry.fromDataSources(dataSources);
    }

    @Bean
    @Primary
    public TransactionExecutorResolver directTransactionExecutorResolver(DirectMyBatisResourceRegistry resources) {
        return resources.getTransactionExecutorResolver();
    }

    @Bean
    @Primary
    public MyBatisAccessResolver directMyBatisAccessResolver(DirectMyBatisResourceRegistry resources,
            IdempotencyProperties properties, StorageRoutingProperties routingProperties,
            ObjectProvider<StorageRouteResolver> routeResolvers,
            ObjectProvider<StorageRouteContext> routeContexts,
            ObjectProvider<IdempotencyPhysicalRouteResolver> physicalRouteResolvers) {
        // 显式说明不合法的配置组合，避免依赖缺失时只得到多层 UnsatisfiedDependencyException。
        if (!properties.isEnabled() || !properties.getMybatis().isEnabled() || !properties.getTransaction().isEnabled()) {
            throw new IllegalStateException("direct storage requires idempotency, MyBatis and transaction to be enabled");
        }
        if (routingProperties.getMode() != StorageRouteMode.DIRECT_DATASOURCE) {
            throw new IllegalStateException("idempotent MyBatis direct storage requires storage-routing mode DIRECT_DATASOURCE");
        }
        requireExactlyOne(routeResolvers, "StorageRouteResolver",
                "enable xjtu.iron.storage-routing.resolver or define one custom StorageRouteResolver bean");
        requireExactlyOne(routeContexts, "StorageRouteContext",
                "enable storage-routing or define one custom StorageRouteContext bean");
        List<IdempotencyPhysicalRouteResolver> routeResolversList = physicalRouteResolvers.orderedStream().toList();
        if (routeResolversList.size() != 1) {
            throw new IllegalStateException("direct storage requires exactly one IdempotencyPhysicalRouteResolver, but found "
                    + routeResolversList.size());
        }
        IdempotencyPhysicalRouteResolver physicalRouteResolver = routeResolversList.get(0);
        if (!(physicalRouteResolver instanceof StorageRoutingIdempotencyPhysicalRouteResolver)) {
            throw new IllegalStateException("direct storage requires the storage-routing physical route resolver");
        }
        StorageRoutingProperties.Resolver routing = routingProperties.getResolver();
        if (routing.isEnabled()) {
            if (routing.getType() == StorageRoutingProperties.ResolverType.FIXED) {
                resources.require(routing.getFixed().getDataSourceKey());
            } else {
                StorageRoutingProperties.Hash hash = routing.getHash();
                var mapping = new RouteMappingStrategyFactory(hash.getDataSourcePrefix(), "validation",
                        hash.getDataSourceIndexWidth(), hash.getTableIndexWidth()).create(hash.getTableIndexMode());
                int total = Math.multiplyExact(hash.getDatabaseCount(), hash.getTablesPerDatabase());
                for (int db = 0; db < hash.getDatabaseCount(); db++) {
                    resources.require(mapping.map(new ShardRouteInfo(db * hash.getTablesPerDatabase(), db, 0, total))
                            .dataSourceKey());
                }
            }
        }
        return resources.getMyBatisAccessResolver();
    }

    @Bean
    @Primary
    public IdempotencyTransactionCoordinator directIdempotencyTransactionCoordinator(StorageRouteContext routes, TransactionExecutorResolver executors) {
        return new StorageRouteAwareIdempotencyTransactionCoordinator(routes, executors);
    }

    @Bean
    public SmartInitializingSingleton directResourceWiringValidator(
            DirectMyBatisResourceRegistry resources,
            @Qualifier("directMyBatisAccessResolver") MyBatisAccessResolver accessResolver,
            @Qualifier("directTransactionExecutorResolver") TransactionExecutorResolver transactionResolver,
            @Qualifier("directIdempotencyTransactionCoordinator") IdempotencyTransactionCoordinator coordinator
    ) {
        return () -> {
            // 公共单库 Resolver 可以同时存在，但 Direct 主链的 Access/事务解析器必须由同一注册表产生。
            if (accessResolver != resources.getMyBatisAccessResolver()
                    || transactionResolver != resources.getTransactionExecutorResolver()
                    || coordinator == null) {
                throw new IllegalStateException(
                        "direct storage requires registry-owned MyBatis and transaction resolvers"
                );
            }
        };
    }

    private static <T> void requireExactlyOne(ObjectProvider<T> provider, String type, String remedy) {
        long count = provider.stream().count();
        if (count != 1) {
            throw new IllegalStateException("direct storage requires exactly one " + type + ", but found " + count
                    + "; " + remedy);
        }
    }
}
