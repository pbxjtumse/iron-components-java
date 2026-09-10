package com.xjtu.iron.storage.routing.core.resolver;

import com.xjtu.iron.storage.routing.api.StorageRoute;
import com.xjtu.iron.storage.routing.api.StorageRouteRequest;
import com.xjtu.iron.storage.routing.api.StorageRouteResolver;
import com.xjtu.iron.storage.routing.api.StorageRoutingException;
import com.xjtu.iron.storage.routing.api.TableIndexMode;
import com.xjtu.iron.storage.routing.core.mapping.RouteMappingStrategyFactory;

import java.util.Objects;

/**
 * 基于 shardId 的分库分表路由器。
 *
 * <p>这是 Phase 2 推荐模型。核心思想不是分别计算数据库和表，而是：</p>
 *
 * <pre>
 * shardKey
 *    |
 *    v
 * shardId = hash(shardKey) % totalShardCount
 *    |
 *    +---- databaseIndex
 *    |
 *    +---- tableIndex
 * </pre>
 *
 * <p>保留原有 builder 作为便捷入口，内部委托 HashShardRouteResolver + RouteMappingStrategy +
 * DefaultStorageRouteResolver。分片信息统一从 StorageRoute.shardInfo() 读取，不再重复存进 attributes。</p>
 */
public final class ShardIdHashStorageRouteResolver implements StorageRouteResolver {

    private final DefaultStorageRouteResolver delegate;

    private ShardIdHashStorageRouteResolver(Builder builder) {
        String dataSourcePrefix = requireText(builder.dataSourcePrefix, "dataSourcePrefix must not be blank");
        String tablePrefix = requireText(builder.tablePrefix, "tablePrefix must not be blank");
        TableIndexMode tableIndexMode = Objects.requireNonNull(builder.tableIndexMode, "tableIndexMode must not be null");
        this.delegate = new DefaultStorageRouteResolver(
                new HashShardRouteResolver(builder.databaseCount, builder.tablesPerDatabase),
                new RouteMappingStrategyFactory(dataSourcePrefix, tablePrefix, 2).create(tableIndexMode));
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public StorageRoute resolve(StorageRouteRequest request) {
        return delegate.resolve(request);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new StorageRoutingException(message);
        }
        return value.trim();
    }

    public static final class Builder {

        private String dataSourcePrefix = "db_";
        private String tablePrefix = "order";
        private int databaseCount;
        private int tablesPerDatabase;
        private TableIndexMode tableIndexMode = TableIndexMode.GLOBAL_TABLE_INDEX;

        public Builder dataSourcePrefix(String dataSourcePrefix) {
            this.dataSourcePrefix = dataSourcePrefix;
            return this;
        }

        public Builder tablePrefix(String tablePrefix) {
            this.tablePrefix = tablePrefix;
            return this;
        }

        public Builder databaseCount(int databaseCount) {
            this.databaseCount = databaseCount;
            return this;
        }

        public Builder tablesPerDatabase(int tablesPerDatabase) {
            this.tablesPerDatabase = tablesPerDatabase;
            return this;
        }

        public Builder tableIndexMode(TableIndexMode tableIndexMode) {
            this.tableIndexMode = tableIndexMode;
            return this;
        }

        public ShardIdHashStorageRouteResolver build() {
            return new ShardIdHashStorageRouteResolver(this);
        }
    }
}
