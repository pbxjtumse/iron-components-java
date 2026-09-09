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
 *
 * <p>使用示例：</p>
 *
 * <pre>{@code
 * HashStorageRouteResolver resolver = HashStorageRouteResolver.builder()
 *         .dataSourcePrefix("order-db-")
 *         .dataSourceCount(10)
 *         .tablePrefix("business_order")
 *         .tableCount(100)
 *         .dataSourceIndexWidth(1)
 *         .tableIndexWidth(3)
 *         .build();
 *
 * StorageRoute route = resolver.resolve(StorageRouteRequest.builder()
 *         .scene("order-create")
 *         .logicalTable("business_order")
 *         .shardKeyName("order_id")
 *         .shardKeyValue("order-10001")
 *         .build());
 * }</pre>
 *
 * <p>如果 dataSourceCount=10、tableCount=100，当前实现的含义是：先选择 10 个 DataSource 中的一个，
 * 再在被选中的库里选择 100 张后缀表中的一张。也就是每个库都有 business_order_000 到
 * business_order_099。若业务想表达“总共 100 张物理表，10 个库，每个库 10 张表”，则 tableCount
 * 应该按“每个库的表数量”来配置，而不是全局总表数量。</p>
 */
public final class HashStorageRouteResolver implements StorageRouteResolver {

    /**
     * 数据源名称前缀。
     *
     * <p>例如 order-db-，再拼接计算出的 dataSourceIndex 后得到 order-db-0、order-db-1。</p>
     */
    private final String dataSourcePrefix;

    /**
     * 表名前缀。
     *
     * <p>例如 business_order，最终拼接 tableIndex 后得到 business_order_017。
     * 如果没有显式配置，则默认使用 request.logicalTable() 作为表名前缀。</p>
     */
    private final String tablePrefix;

    /**
     * 数据源数量。
     *
     * <p>例如 10 表示路由到 0 到 9 这 10 个数据源下标。</p>
     */
    private final int dataSourceCount;

    /**
     * 每个数据源内的分表数量。
     *
     * <p>例如 100 表示当前选中的库内有 000 到 099 这 100 张同前缀物理表。</p>
     */
    private final int tableCount;

    /**
     * 数据源下标宽度。
     *
     * <p>例如 width=2 时，下标 1 会格式化为 01，最终得到 order-db-01。</p>
     */
    private final int dataSourceIndexWidth;

    /**
     * 表下标宽度。
     *
     * <p>例如 width=3 时，下标 17 会格式化为 017，最终得到 business_order_017。</p>
     */
    private final int tableIndexWidth;

    /**
     * 生成的 StorageRoute 使用的路由模式。
     */
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

    /**
     * 根据请求计算 StorageRoute。
     *
     * <p>核心流程：</p>
     * <pre>{@code
     * 1. 读取 request.shardKeyValue()
     * 2. 对 shardKeyValue 做稳定 hash
     * 3. dataSourceIndex = floorMod(hash, dataSourceCount)
     * 4. tableIndex      = floorMod(hash, tableCount)
     * 5. 拼接 dataSourceKey 和 tableName
     * 6. 把 logicalTable / dataSourceIndex / tableIndex 写入 attributes，方便调试和后续组件使用
     * }</pre>
     */
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

    /**
     * 计算稳定 hash。
     *
     * <p>第一版先使用 String.valueOf(value).hashCode()，保证同一个 shardKeyValue 在同版本 JDK 下得到稳定结果。
     * 后续如果要做生产级一致性，可以抽 HashAlgorithm SPI，避免扩容或 JDK 差异带来的规则风险。</p>
     */
    private static int stableHash(Object shardKeyValue) {
        String value = String.valueOf(Objects.requireNonNull(shardKeyValue, "shardKeyValue must not be null"));
        return value.hashCode();
    }

    /**
     * 按指定宽度格式化下标。
     */
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

    /**
     * HashStorageRouteResolver 构造器。
     */
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

        /**
         * 设置数据源前缀，例如 order-db-。
         */
        public Builder dataSourcePrefix(String dataSourcePrefix) {
            this.dataSourcePrefix = dataSourcePrefix;
            return this;
        }

        /**
         * 设置表名前缀，例如 business_order。未设置时使用 request.logicalTable()。
         */
        public Builder tablePrefix(String tablePrefix) {
            this.tablePrefix = tablePrefix;
            return this;
        }

        /**
         * 设置数据源数量，例如 10。
         */
        public Builder dataSourceCount(int dataSourceCount) {
            this.dataSourceCount = dataSourceCount;
            return this;
        }

        /**
         * 设置每个数据源内的表数量，例如 100。
         */
        public Builder tableCount(int tableCount) {
            this.tableCount = tableCount;
            return this;
        }

        /**
         * 设置数据源下标宽度，例如 2 会生成 00、01、02。
         */
        public Builder dataSourceIndexWidth(int dataSourceIndexWidth) {
            this.dataSourceIndexWidth = dataSourceIndexWidth;
            return this;
        }

        /**
         * 设置表下标宽度，例如 3 会生成 000、001、017。
         */
        public Builder tableIndexWidth(int tableIndexWidth) {
            this.tableIndexWidth = tableIndexWidth;
            return this;
        }

        /**
         * 设置生成路由的模式，默认 DIRECT_DATASOURCE。
         */
        public Builder mode(StorageRouteMode mode) {
            this.mode = mode;
            return this;
        }

        public HashStorageRouteResolver build() {
            return new HashStorageRouteResolver(this);
        }
    }
}
