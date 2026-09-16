package com.xjtu.iron.idempotent.starter.autoconfigure;

import com.xjtu.iron.idempotent.integration.storage.routing.StorageRoutingIdempotencyJdbcRouteResolver;
import com.xjtu.iron.idempotent.integration.storage.routing.DefaultIdempotencyRouteContextFactory;
import com.xjtu.iron.idempotent.integration.storage.routing.IdempotencyRouteContextFactory;
import com.xjtu.iron.idempotent.provider.jdbc.routing.IdempotencyJdbcRouteResolver;
import com.xjtu.iron.idempotent.starter.properties.IdempotencyProperties;
import com.xjtu.iron.idempotent.starter.properties.IdempotencyStorageRoutingProperties;
import com.xjtu.iron.storage.routing.api.StorageRouteContext;
import com.xjtu.iron.storage.routing.api.mapping.RouteMappingStrategy;
import com.xjtu.iron.storage.routing.api.resolver.StorageRouteResolver;
import com.xjtu.iron.storage.routing.core.mapping.RouteMappingStrategyFactory;
import com.xjtu.iron.storage.routing.integration.relational.StorageRouteToSqlRouteBridge;
import com.xjtu.iron.storage.routing.starter.autoconfigure.StorageRoutingProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 幂等 JDBC Storage 与 Storage Routing 的自动集成。
 *
 * <p>Storage Routing 的全局 resolver 决定“一个分片键属于哪个 shard”；幂等组件不能直接复用它的 business table
 * 物理位置，因为业务表、幂等表、Outbox 表虽然共享 shardInfo，却必须各自映射自己的 physical table。</p>
 *
 * <p>因此本配置会额外创建一份 idempotencyRouteMappingStrategy：数据库拓扑、编号模式和宽度复用
 * StorageRoutingProperties；幂等 logicalTable/tablePrefix 则来自独立的 IdempotencyStorageRoutingProperties。
 * 固定单库单表仍使用 xjtu.iron.idempotent.jdbc.table-name，不再和分片表前缀混为一个配置语义。</p>
 */
@AutoConfiguration(
        afterName = "com.xjtu.iron.storage.routing.starter.autoconfigure.StorageRoutingAutoConfiguration",
        before = IdempotencyAutoConfiguration.class
)
@EnableConfigurationProperties({
        IdempotencyProperties.class,
        IdempotencyStorageRoutingProperties.class,
        StorageRoutingProperties.class
})
@ConditionalOnClass({StorageRoutingIdempotencyJdbcRouteResolver.class, RouteMappingStrategyFactory.class, StorageRoutingProperties.class})
@ConditionalOnProperty(prefix = "xjtu.iron.idempotent.jdbc.routing", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IdempotencyStorageRoutingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdempotencyRouteContextFactory.class)
    public IdempotencyRouteContextFactory idempotencyRouteContextFactory(
            IdempotencyStorageRoutingProperties idempotencyRouting
    ) {
        return new DefaultIdempotencyRouteContextFactory(idempotencyRouting.getLogicalTable());
    }

    /**
     * 为幂等表单独创建物理映射策略。
     *
     * <p>这里故意不复用 StorageRouteResolver 返回的 physical table：默认 resolver 的 tablePrefix 通常属于业务表。
     * 我们只共享 shardInfo，然后使用幂等 Storage 自己的 tablePrefix 再映射一次。</p>
     */
    @Bean(name = "idempotencyRouteMappingStrategy")
    @ConditionalOnBean(StorageRouteResolver.class)
    @ConditionalOnMissingBean(name = "idempotencyRouteMappingStrategy")
    public RouteMappingStrategy idempotencyRouteMappingStrategy(
            IdempotencyStorageRoutingProperties idempotencyRouting,
            StorageRoutingProperties storageRouting
    ) {
        StorageRoutingProperties.Resolver routing = storageRouting.getResolver();
        return new RouteMappingStrategyFactory(
                routing.getDataSourcePrefix(),
                idempotencyRouting.getTablePrefix(),
                routing.getDataSourceIndexWidth(),
                routing.getTableIndexWidth())
                .create(routing.getTableIndexMode());
    }

    @Bean
    @ConditionalOnBean({StorageRouteResolver.class, StorageRouteContext.class, StorageRouteToSqlRouteBridge.class})
    @ConditionalOnMissingBean(IdempotencyJdbcRouteResolver.class)
    public IdempotencyJdbcRouteResolver storageRoutingIdempotencyJdbcRouteResolver(
            StorageRouteResolver storageRouteResolver,
            StorageRouteContext storageRouteContext,
            @Qualifier("idempotencyRouteMappingStrategy") RouteMappingStrategy idempotencyMappingStrategy,
            StorageRouteToSqlRouteBridge relationalBridge,
            IdempotencyStorageRoutingProperties idempotencyRouting
    ) {
        return new StorageRoutingIdempotencyJdbcRouteResolver(
                idempotencyRouting.getLogicalTable(),
                idempotencyRouting.getTablePrefix(),
                storageRouteResolver,
                storageRouteContext,
                idempotencyMappingStrategy,
                relationalBridge);
    }
}
