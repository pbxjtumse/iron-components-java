package com.xjtu.iron.storage.routing.core.learning;

import com.xjtu.iron.storage.routing.api.key.CompositeShardKey;
import com.xjtu.iron.storage.routing.api.key.ShardKey;
import com.xjtu.iron.storage.routing.api.mapping.TableIndexMode;
import com.xjtu.iron.storage.routing.api.route.RouteContext;
import com.xjtu.iron.storage.routing.core.context.ThreadLocalStorageRouteContext;
import com.xjtu.iron.storage.routing.core.mapping.RouteMappingStrategyFactory;
import com.xjtu.iron.storage.routing.core.resolver.DefaultStorageRouteResolver;
import com.xjtu.iron.storage.routing.core.resolver.HashShardResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 入门阅读入口：没有数据库、Spring、幂等和测试夹具，只有输入、计算、表映射与作用域。 */
class RoutingBasicsTest {
    @Test
    void routeBusinessAndIdempotencyTablesToTheSameShard() {
        // 1. 业务决定分片依据。订单写入和查询必须使用一致的键与规则。
        var key = CompositeShardKey.of(ShardKey.of("user_id", "user-1001"));
        var input = RouteContext.builder().logicalTable("business_order").shardKey(key).build();

        // 2. 先计算分片，再映射业务物理表。logicalTable 本身不会自动选择 mapper。
        var hash = new HashShardResolver(10, 10);
        var orderMapping = new RouteMappingStrategyFactory("db_", "business_order", 2, 2).create(TableIndexMode.LOCAL_TABLE_INDEX);
        var resolver = new DefaultStorageRouteResolver(hash, orderMapping);
        var route = resolver.resolve(input);
        assertThat(route.shardInfo().databaseIndex()).isEqualTo(route.shardInfo().shardId() / 10);
        assertThat(route.shardInfo().localTableIndex()).isEqualTo(route.shardInfo().shardId() % 10);

        // 3. 幂等表复用同一个分片结果，只换表族，不再 hash 幂等 key。
        var idempotencyMapping = new RouteMappingStrategyFactory("db_", "iron_idempotency_record", 2, 2).create(TableIndexMode.LOCAL_TABLE_INDEX);
        var idempotencyLocation = idempotencyMapping.map(route.shardInfo());
        assertThat(idempotencyLocation.dataSourceKey()).isEqualTo(route.dataSourceKey());
        assertThat(idempotencyLocation.tableName()).isNotEqualTo(route.tableName());

        // 4. 作用域只是临时传递计算结果，不开启事务；最外层 close 后清理。
        var currentRoutes = new ThreadLocalStorageRouteContext();
        try (var scope = currentRoutes.open(route)) {
            assertThat(currentRoutes.requireCurrent()).isSameAs(route);
        }
        assertThat(currentRoutes.current()).isEmpty();
    }
}
