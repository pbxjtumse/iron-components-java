package com.xjtu.iron.storage.routing.core.resolver;

import com.xjtu.iron.storage.routing.api.StorageRoute;
import com.xjtu.iron.storage.routing.api.StorageRouteMode;
import com.xjtu.iron.storage.routing.api.StorageRouteRequest;
import com.xjtu.iron.storage.routing.api.StorageRouteResolver;
import com.xjtu.iron.storage.routing.api.StorageRoutingException;

import java.util.Objects;

/**
 * 基于 hash 的最小路由解析器。
 *
 * <p>该实现用于 Phase 2 第一版验证，不承担生产级分库分表中间件职责。它只根据 shardKeyValue
 * 计算 dataSourceKey 和 tableName，用于打通 StorageRoute 的概念、上下文传播和组件接入。</p>
 */
public final class HashStorageRouteResolver implements StorageRouteResolver {

    private final String dataSourcePrefix;
    private final String tablePrefix;
    private final int dataSourceCount;
    private final int tableCount;
    private final int dataSourceIndexWidth;
    private final int tableIndexWidth;
    private final StorageRouteMode mode;

    private HashStorageRouteResolver(Builder builder) {
        this.dataSourcePrefix = requireText(builder.dataSourcePrefix, "dataSourcePrefix must not be blank");
        this.tablePrefix = normalize(builder.tablePrefix);
        this.dataSourceCount = requirePositive(builder.dataSourceCount, "dataSourceCount must be positive");
        this.tableCount = requirePositive(builder.tableCount, "tableCount must be positive");
        this.dataSourceIndexWidth = requireNonNegative(builder.dataSourceIndexWidth, "dataSourceIndexWidth must not be negative");
        this.tableIndexWidth = requireNonNegative(builder.tableIndexWidth, "tableIndexWidth must not be negative");
        this.mode = Objects.requireNonNull(builder.mode, "mode must not be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public StorageRoute resolve(StorageRouteRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        int hash = stableHash(request.shardKeyValue());
        int dataSourceIndex = Math.floorMod(hash, dataSourceCount);
        int tableIndex = Math.floorMod(hash, tableCount);
        String resolvedTablePrefix = tablePrefix == null ? request.logicalTable() : tablePrefix;

        return StorageRoute.builder()
                .mode(mode)
                .routeName(request.scene())
                .dataSourceKey(dataSourcePrefix + formatIndex(dataSourceIndex, dataSourceIndexWidth))
                .tableName(resolvedTablePrefix + "_" + formatIndex(tableIndex, tableIndexWidth))
                .shardKeyName(request.shardKeyName())
                .shardKeyValue(request.shardKeyValue())
                .attribute("logicalTable", request.logicalTable())
                .attribute("dataSourceIndex", dataSourceIndex)
                .attribute("tableIndex", tableIndex)
                .build();
    }

    private static int stableHash(Object shardKeyValue) {
        String value = String.valueOf(Objects.requireNonNull(shardKeyValue, "shardKeyValue must not be null"));
        return value.hashCode();
    }

    private static String formatIndex(int index, int width) {
        if (width <= 0) {
            return String.valueOf(index);
        }
        return String.format("%0" + width + "d", index);
    }

    private static String requireText(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new StorageRoutingException(message);
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static int requirePositive(int value, String message) {
        if (value <= 0) {
            throw new StorageRoutingException(message);
        }
        return value;
    }

    private static int requireNonNegative(int value, String message) {
        if (value < 0) {
            throw new StorageRoutingException(message);
        }
        return value;
    }

    public static final class Builder {

        private String dataSourcePrefix;
        private String tablePrefix;
        private int dataSourceCount;
        private int tableCount;
        private int dataSourceIndexWidth = 1;
        private int tableIndexWidth = 3;
        private StorageRouteMode mode = StorageRouteMode.DIRECT_DATASOURCE;

        private Builder() {
        }

        public Builder dataSourcePrefix(String dataSourcePrefix) {
            this.dataSourcePrefix = dataSourcePrefix;
            return this;
        }

        public Builder tablePrefix(String tablePrefix) {
            this.tablePrefix = tablePrefix;
            return this;
        }

        public Builder dataSourceCount(int dataSourceCount) {
            this.dataSourceCount = dataSourceCount;
            return this;
        }

        public Builder tableCount(int tableCount) {
            this.tableCount = tableCount;
            return this;
        }

        public Builder dataSourceIndexWidth(int dataSourceIndexWidth) {
            this.dataSourceIndexWidth = dataSourceIndexWidth;
            return this;
        }

        public Builder tableIndexWidth(int tableIndexWidth) {
            this.tableIndexWidth = tableIndexWidth;
            return this;
        }

        public Builder mode(StorageRouteMode mode) {
            this.mode = mode;
            return this;
        }

        public HashStorageRouteResolver build() {
            return new HashStorageRouteResolver(this);
        }
    }
}
