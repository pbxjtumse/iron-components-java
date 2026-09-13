package com.xjtu.iron.idempotent.starter.autoconfigure;

import com.xjtu.iron.idempotent.integration.storage.routing.StorageRoutingIdempotencyJdbcRouteResolver;
import com.xjtu.iron.idempotent.provider.jdbc.routing.IdempotencyJdbcRouteResolver;
import com.xjtu.iron.idempotent.starter.properties.IdempotencyProperties;
import com.xjtu.iron.storage.routing.api.StorageRouteContext;
import com.xjtu.iron.storage.routing.api.resolver.StorageRouteResolver;
import com.xjtu.iron.storage.routing.integration.relational.StorageRouteToSqlRouteBridge;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 幂等 JDBC Storage 与 Storage Routing 的可选自动集成。
 *
 * <p>只有业务工程真正引入并启用了 storage-routing-starter，且 Resolver / Context / Relational bridge
 * 都已存在时才生效。否则 IdempotencyAutoConfiguration 会装配固定单库单表路由，保持原有使用方式。</p>
 */
@AutoConfiguration(
        afterName = "com.xjtu.iron.storage.routing.starter.autoconfigure.StorageRoutingAutoConfiguration",
        before = IdempotencyAutoConfiguration.class
)
@ConditionalOnClass(StorageRoutingIdempotencyJdbcRouteResolver.class)
@ConditionalOnProperty(prefix = "xjtu.iron.idempotent.jdbc.routing", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IdempotencyStorageRoutingAutoConfiguration {

    @Bean
    @ConditionalOnBean({StorageRouteResolver.class, StorageRouteContext.class, StorageRouteToSqlRouteBridge.class})
    @ConditionalOnMissingBean(IdempotencyJdbcRouteResolver.class)
    public IdempotencyJdbcRouteResolver storageRoutingIdempotencyJdbcRouteResolver(
            StorageRouteResolver storageRouteResolver,
            StorageRouteContext storageRouteContext,
            StorageRouteToSqlRouteBridge relationalBridge,
            IdempotencyProperties properties
    ) {
        // V2 先让现有 jdbc.table-name 同时作为“固定物理表名 / 路由逻辑表名”的基线配置，
        // Storage Routing 启用后最终物理表仍由 RouteMappingStrategy 决定，不会直接使用该名称执行 SQL。
        return new StorageRoutingIdempotencyJdbcRouteResolver(
                properties.getJdbc().getTableName(), storageRouteResolver, storageRouteContext, relationalBridge);
    }
}
