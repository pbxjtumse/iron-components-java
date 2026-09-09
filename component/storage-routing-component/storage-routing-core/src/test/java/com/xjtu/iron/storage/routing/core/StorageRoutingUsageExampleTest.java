package com.xjtu.iron.storage.routing.core;

import com.xjtu.iron.storage.routing.api.StorageRoute;
import com.xjtu.iron.storage.routing.api.StorageRouteContext;
import com.xjtu.iron.storage.routing.api.StorageRouteRequest;
import com.xjtu.iron.storage.routing.api.StorageRouteResolver;
import com.xjtu.iron.storage.routing.api.StorageRouteScope;
import com.xjtu.iron.storage.routing.core.context.ThreadLocalStorageRouteContext;
import com.xjtu.iron.storage.routing.core.resolver.FixedStorageRouteResolver;
import com.xjtu.iron.storage.routing.core.resolver.HashStorageRouteResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 面向阅读的使用样例。
 *
 * <p>这个测试不是为了覆盖复杂算法，而是为了让第一次看 storage-routing 的人知道：
 * logicalTable、scene、shardKeyName、shardKeyValue、StorageRouteContext 应该如何串起来。</p>
 */
class StorageRoutingUsageExampleTest {

    @Test
    void shouldResolveOrderRouteAndBindItToCurrentContext() {
        // 1. 创建一个最小 hash 路由解析器。
        // dataSourcePrefix + dataSourceIndex -> order-db-0 / order-db-1 / ... / order-db-9
        // tablePrefix + tableIndex           -> business_order_000 / ... / business_order_099
        StorageRouteResolver resolver = HashStorageRouteResolver.builder()
                .dataSourcePrefix("order-db-")
                .dataSourceCount(10)
                .tablePrefix("business_order")
                .tableCount(100)
                .dataSourceIndexWidth(1)
                .tableIndexWidth(3)
                .build();

        // 2. 构造一次业务路由请求。
        // scene 表示业务场景：这次是创建订单。
        // logicalTable 表示逻辑表族：业务上访问的是订单表，不关心物理后缀。
        // shardKeyName/shardKeyValue 表示分片依据：根据 order_id=order-10001 算库表。
        StorageRouteRequest request = StorageRouteRequest.builder()
                .scene("order-create")
                .logicalTable("business_order")
                .shardKeyName("order_id")
                .shardKeyValue("order-10001")
                .attribute("idempotencyTable", "iron_idempotency_record")
                .attribute("outboxTable", "iron_outbox")
                .build();

        // 3. Resolver 输出路由结果。
        // route 只回答“去哪”，不执行 SQL，也不打开 Connection。
        StorageRoute route = resolver.resolve(request);

        assertThat(route.routeName()).isEqualTo("order-create");
        assertThat(route.dataSourceKey()).startsWith("order-db-");
        assertThat(route.tableName()).startsWith("business_order_");
        assertThat(route.shardKeyName()).isEqualTo("order_id");
        assertThat(route.shardKeyValue()).isEqualTo("order-10001");
        assertThat(route.attribute("logicalTable")).isEqualTo("business_order");
        assertThat(route.attribute("dataSourceIndex")).isInstanceOf(Integer.class);
        assertThat(route.attribute("tableIndex")).isInstanceOf(Integer.class);

        // 4. 把路由绑定到当前调用链。
        // 后续业务 Repository、IdempotencyStorage、OutboxStorage 都应该读取这同一份 route。
        StorageRouteContext context = new ThreadLocalStorageRouteContext();
        assertThat(context.current()).isEmpty();

        try (StorageRouteScope ignored = context.open(route)) {
            assertThat(context.requireCurrent()).isEqualTo(route);

            // 这两个断言模拟后续组件读取同一份 route：
            // - 业务 Repository 后续可以根据 tableName 拼物理表或交给下游 adapter
            // - IdempotencyStorage 后续可以根据 dataSourceKey 保证和业务数据同库
            assertThat(context.requireCurrent().dataSourceKey()).isEqualTo(route.dataSourceKey());
            assertThat(context.requireCurrent().shardKeyValue()).isEqualTo("order-10001");
        }

        // 5. 离开作用域后必须清理，避免线程池复用时串路由。
        assertThat(context.current()).isEmpty();
    }

    @Test
    void shouldUseFixedResolverForSingleDatabaseOrDemo() {
        // 固定路由适合单库、Demo、测试。
        // 它不关心 request 里传入的 logicalTable / shardKeyValue，永远返回同一个 route。
        StorageRoute fixedRoute = StorageRoute.direct("order-db-0", "business_order");
        StorageRouteResolver resolver = new FixedStorageRouteResolver(fixedRoute);

        StorageRoute actual = resolver.resolve(StorageRouteRequest.of("business_order", "order_id", "order-10002"));

        assertThat(actual).isEqualTo(fixedRoute);
    }
}
