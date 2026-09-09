package com.xjtu.iron.storage.routing.core.resolver;

import com.xjtu.iron.storage.routing.api.StorageRoute;
import com.xjtu.iron.storage.routing.api.StorageRouteMode;
import com.xjtu.iron.storage.routing.api.StorageRouteRequest;
import com.xjtu.iron.storage.routing.api.StorageRouteResolver;
import com.xjtu.iron.storage.routing.api.StorageRoutingException;
import com.xjtu.iron.storage.routing.api.TableIndexMode;

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
 * <p>这样后续接 ShardingSphere-JDBC、MyCAT 或自研路由时，都可以围绕统一 shardId 演进。</p>
 */
public final class ShardIdHashStorageRouteResolver implements StorageRouteResolver {

    private final String dataSourcePrefix;
    private final String tablePrefix;
    private final int databaseCount;
    private final int tablesPerDatabase;
    private final TableIndexMode tableIndexMode;

    private ShardIdHashStorageRouteResolver(Builder builder) {
        this.dataSourcePrefix = requireText(builder.dataSourcePrefix, "dataSourcePrefix must not be blank");
        this.tablePrefix = requireText(builder.tablePrefix, "tablePrefix must not be blank");
        this.databaseCount = requirePositive(builder.databaseCount, "databaseCount must be positive");
        this.tablesPerDatabase = requirePositive(builder.tablesPerDatabase, "tablesPerDatabase must be positive");
        this.tableIndexMode = Objects.requireNonNull(builder.tableIndexMode, "tableIndexMode must not be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public StorageRoute resolve(StorageRouteRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        int totalShardCount = databaseCount * tablesPerDatabase;
        int shardId = Math.floorMod(String.valueOf(request.shardKeyValue()).hashCode(), totalShardCount);

        int databaseIndex = shardId / tablesPerDatabase;
        int localTableIndex = shardId % tablesPerDatabase;

        int physicalTableIndex = tableIndexMode == TableIndexMode.GLOBAL_TABLE_INDEX
                ? shardId
                : localTableIndex;

        return StorageRoute.builder()
                .mode(StorageRouteMode.DIRECT_DATASOURCE)
                .routeName(request.scene())
                .dataSourceKey(dataSourcePrefix + String.format("%02d", databaseIndex))
                .tableName(tablePrefix + "_" + String.format("%02d", physicalTableIndex))
                .shardKeyName(request.shardKeyName())
                .shardKeyValue(request.shardKeyValue())
                .attribute("shardId", shardId)
                .attribute("databaseIndex", databaseIndex)
                .attribute("localTableIndex", localTableIndex)
                .attribute("physicalTableIndex", physicalTableIndex)
                .attribute("tableIndexMode", tableIndexMode.name())
                .attribute("totalShardCount", totalShardCount)
                .build();
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new StorageRoutingException(message);
        }
        return value.trim();
    }

    private static int requirePositive(int value, String message) {
        if (value <= 0) {
            throw new StorageRoutingException(message);
        }
        return value;
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
