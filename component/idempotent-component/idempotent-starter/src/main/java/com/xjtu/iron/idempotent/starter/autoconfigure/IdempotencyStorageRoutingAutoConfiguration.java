package com.xjtu.iron.idempotent.starter.autoconfigure;

import com.xjtu.iron.idempotent.integration.storage.routing.StorageRoutingIdempotencyJdbcRouteResolver;
import com.xjtu.iron.idempotent.provider.jdbc.routing.IdempotencyJdbcRouteResolver;
import com.xjtu.iron.idempotent.starter.properties.IdempotencyProperties;
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
 * StorageRoutingProperties，表前缀使用 xjtu.iron.idempotent.jdbc.table-name。固定单库单表模式仍由
 * FixedIdempotencyJdbcRouteResolver 负责，不经过本配置。</p>
 */
@AutoConfiguration(
        afterName = "com.xjtu.iron.storage.routing.starter.autoconfigure.StorageRoutingAutoConfiguration",
        before = IdempotencyAutoConfiguration.class
)
@EnableConfigurationProperties({IdempotencyProperties.class, StorageRoutingProperties.class})
@ConditionalOnClass({StorageRoutingIdempotencyJdbcRouteResolver.class, RouteMappingStrategyFactory.class, StorageRoutingProperties.class})
@ConditionalOnProperty(prefix = "xjtu.iron.idempotent.jdbc.routing", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IdempotencyStorageRoutingAutoConfiguration {

    /**
     * 为幂等表单独创建物理映射策略。
     *
     * <p>这里故意不复用 StorageRouteResolver 返回的 physical table：默认 resolver 的 tablePrefix 通常属于业务表。
     * 我们只共享 shardInfo，然后用幂等表自己的 prefix 再映射一次。</p>
     */
    @Bean(name = "idempotencyRouteMappingStrategy")
    @ConditionalOnBean(StorageRouteResolver.class)
    @ConditionalOnMissingBean(name = "idempotencyRouteMappingStrategy")
    public RouteMappingStrategy idempotencyRouteMappingStrategy(
            IdempotencyProperties idempotencyProperties,
            StorageRoutingProperties storageRoutingProperties
    ) {
        StorageRoutingProperties.Resolver routing = storageRoutingProperties.getResolver();
        return new RouteMappingStrategyFactory(
                routing.getDataSourcePrefix(),
                idempotencyProperties.getJdbc().getTableName(),
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
            IdempotencyProperties properties
    ) {
        return new StorageRoutingIdempotencyJdbcRouteResolver(
                properties.getJdbc().getTableName(),
                storageRouteResolver,
                storageRouteContext,
                idempotencyMappingStrategy,
                relationalBridge);
    }
}
