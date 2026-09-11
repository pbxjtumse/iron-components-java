package com.xjtu.iron.storage.routing.starter.autoconfigure;

import com.xjtu.iron.relational.api.statement.SqlRoute;
import com.xjtu.iron.storage.routing.api.StorageRouteContext;
import com.xjtu.iron.storage.routing.api.resolver.StorageRouteResolver;
import com.xjtu.iron.storage.routing.core.context.ThreadLocalStorageRouteContext;
import com.xjtu.iron.storage.routing.core.resolver.ShardIdHashStorageRouteResolver;
import com.xjtu.iron.storage.routing.integration.relational.DefaultStorageRouteToSqlRouteBridge;
import com.xjtu.iron.storage.routing.integration.relational.StorageRouteToSqlRouteBridge;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Storage Routing 的 Spring Boot 自动配置。
 *
 * <p>Starter 默认提供线程内路由上下文和 Relational Access bridge。默认哈希 resolver 需要显式配置启用，
 * 避免在业务未声明库表拓扑时生成错误落点。</p>
 */
@AutoConfiguration
@ConditionalOnClass({StorageRouteContext.class, SqlRoute.class})
@ConditionalOnProperty(prefix = "xjtu.iron.storage-routing", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(StorageRoutingProperties.class)
public class StorageRoutingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public StorageRouteContext storageRouteContext() {
        return new ThreadLocalStorageRouteContext();
    }

    @Bean
    @ConditionalOnMissingBean
    public StorageRouteToSqlRouteBridge storageRouteToSqlRouteBridge() {
        return new DefaultStorageRouteToSqlRouteBridge();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "xjtu.iron.storage-routing.resolver", name = "enabled", havingValue = "true")
    public StorageRouteResolver storageRouteResolver(StorageRoutingProperties properties) {
        StorageRoutingProperties.Resolver resolver = properties.getResolver();
        return ShardIdHashStorageRouteResolver.builder()
                .dataSourcePrefix(resolver.getDataSourcePrefix())
                .tablePrefix(resolver.getTablePrefix())
                .databaseCount(resolver.getDatabaseCount())
                .tablesPerDatabase(resolver.getTablesPerDatabase())
                .tableIndexWidth(resolver.getTableIndexWidth())
                .tableIndexMode(resolver.getTableIndexMode())
                .build();
    }
}
