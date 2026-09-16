package com.xjtu.iron.idempotent.starter.autoconfigure;

import com.xjtu.iron.idempotent.api.execution.IdempotencyExecutor;
import com.xjtu.iron.idempotent.integration.storage.routing.IdempotencyRouteContextFactory;
import com.xjtu.iron.idempotent.integration.storage.routing.StorageRouteAwareIdempotencyExecutor;
import com.xjtu.iron.storage.routing.api.StorageRouteContext;
import com.xjtu.iron.storage.routing.api.resolver.StorageRouteResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 在核心 IdempotencyExecutor 外层绑定一次执行级 StorageRoute。 */
@AutoConfiguration(after = IdempotencyAutoConfiguration.class)
@ConditionalOnClass(StorageRouteAwareIdempotencyExecutor.class)
@ConditionalOnBean({
        IdempotencyExecutor.class,
        IdempotencyRouteContextFactory.class,
        StorageRouteResolver.class,
        StorageRouteContext.class
})
public class IdempotencyStorageRoutingExecutionAutoConfiguration {

    @Bean
    @Primary
    @ConditionalOnBean(name = "idempotencyExecutor")
    @ConditionalOnMissingBean(name = "storageRouteAwareIdempotencyExecutor")
    public IdempotencyExecutor storageRouteAwareIdempotencyExecutor(
            @Qualifier("idempotencyExecutor") IdempotencyExecutor delegate,
            IdempotencyRouteContextFactory routeContextFactory,
            StorageRouteResolver storageRouteResolver,
            StorageRouteContext storageRouteContext
    ) {
        return new StorageRouteAwareIdempotencyExecutor(
                delegate,
                routeContextFactory,
                storageRouteResolver,
                storageRouteContext);
    }
}
