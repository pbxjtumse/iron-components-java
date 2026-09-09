package com.xjtu.iron.storage.routing.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 一次存储访问的路由结果。
 *
 * <p>StorageRoute 不是 JDBC 连接，也不是 SQL。它只表达“本次业务相关的存储记录应该去哪”。
 * 后续 Idempotency / Outbox / Task / Message 等技术组件都可以通过同一份 StorageRoute
 * 保证技术记录与业务记录落到同一个路由位置。</p>
 *
 * <p>一个典型订单场景如下：</p>
 *
 * <pre>{@code
 * StorageRoute route = StorageRoute.builder()
 *         .mode(StorageRouteMode.DIRECT_DATASOURCE)
 *         .routeName("order-create")
 *         .dataSourceKey("order-db-1")
 *         .tableName("business_order_017")
 *         .shardKeyName("order_id")
 *         .shardKeyValue("order-10001")
 *         .attribute("logicalTable", "business_order")
 *         .build();
 * }</pre>
 *
 * <p>上面这份 route 的含义是：订单 order-10001 相关的业务记录和技术组件记录，
 * 本次应该围绕 order-db-1 这个存储位置工作。至于 SQL 怎么执行、Connection 怎么获取，
 * 仍然由 Relational Access / Spring Transaction 等组件负责。</p>
 */
public final class StorageRoute {

    /**
     * 路由模式。
     *
     * <p>它说明下面的 dataSourceKey / tableName 应该如何解释。
     * 例如 DIRECT_DATASOURCE 表示应用内直接选择 DataSource；SHARDINGSPHERE_JDBC 表示
     * 应用只使用逻辑 DataSource，具体物理库表由 ShardingSphere-JDBC 处理。</p>
     */
    private final StorageRouteMode mode;

    /**
     * 路由场景名称。
     *
     * <p>routeName 不是表名，也不是库名，而是一个便于观测、排查、区分规则的业务场景名。
     * 常见取值：order-create、idempotency-try-acquire、outbox-save、task-recover。</p>
     */
    private final String routeName;

    /**
     * 数据源标识。
     *
     * <p>在 DIRECT_DATASOURCE 模式下，它通常会被映射成一个真实 DataSource，例如 order-db-1。
     * 后续 Relational Access 的 RoutingDataSourceResolver 可以根据这个值找到目标 DataSource。</p>
     *
     * <p>在 SHARDINGSPHERE_JDBC / PROXY 模式下，该字段可以为空或表示逻辑数据源，
     * 因为真实物理库通常由中间件决定。</p>
     */
    private final String dataSourceKey;

    /**
     * 表名。
     *
     * <p>在 DIRECT_DATASOURCE 模式下，它可以是物理表名，例如 business_order_017。
     * 在 SHARDINGSPHERE_JDBC 模式下，它更多时候是逻辑表名，例如 business_order，
     * 物理表后缀由 ShardingSphere 根据分片键路由和改写。</p>
     */
    private final String tableName;

    /**
     * 分片键字段名。
     *
     * <p>例如 order_id、user_id、merchant_id、tenant_id。它用于告诉下游组件：
     * 本次路由是基于哪个字段做出的。</p>
     */
    private final String shardKeyName;

    /**
     * 分片键字段值。
     *
     * <p>例如 order-10001。HashStorageRouteResolver 会基于这个值计算库下标和表下标。
     * 技术组件也可以把它写入 SQL 条件，帮助 ShardingSphere-JDBC 精准路由。</p>
     */
    private final Object shardKeyValue;

    /**
     * 扩展属性。
     *
     * <p>用于携带不适合固定成主字段的信息，例如 logicalTable、dataSourceIndex、tableIndex、
     * tenantId、region、业务表名、幂等表名等。第一版先保留 Map，避免过早把模型定死。</p>
     */
    private final Map<String, Object> attributes;

    private StorageRoute(Builder builder) {
        this.mode = Objects.requireNonNull(builder.mode, "mode must not be null");
        this.routeName = normalize(builder.routeName);
        this.dataSourceKey = normalize(builder.dataSourceKey);
        this.tableName = normalize(builder.tableName);
        this.shardKeyName = normalize(builder.shardKeyName);
        this.shardKeyValue = builder.shardKeyValue;
        this.attributes = immutableCopy(builder.attributes);
    }

    /**
     * 创建直连 DataSource 模式路由。
     *
     * <p>适合测试、单库、或者应用自己已经明确知道目标 DataSource 和表名的场景。</p>
     *
     * <pre>{@code
     * StorageRoute route = StorageRoute.direct("order-db-1", "business_order_017");
     * }</pre>
     *
     * @param dataSourceKey 数据源标识，例如 order-db-1
     * @param tableName 表名，例如 business_order_017
     * @return 直连模式路由结果
     */
    public static StorageRoute direct(String dataSourceKey, String tableName) {
        return builder()
                .mode(StorageRouteMode.DIRECT_DATASOURCE)
                .dataSourceKey(dataSourceKey)
                .tableName(tableName)
                .build();
    }

    /**
     * 创建 Builder。
     *
     * <p>复杂场景建议使用 builder，因为可以同时设置 routeName、shardKeyName、shardKeyValue、attributes。</p>
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 返回路由模式，用来判断 dataSourceKey / tableName 的解释方式。
     */
    public StorageRouteMode mode() {
        return mode;
    }

    /**
     * 返回路由场景名称，例如 order-create。
     */
    public String routeName() {
        return routeName;
    }

    /**
     * 返回数据源标识，例如 order-db-1。
     */
    public String dataSourceKey() {
        return dataSourceKey;
    }

    /**
     * 返回表名。它可能是物理表名，也可能是逻辑表名，取决于 mode。
     */
    public String tableName() {
        return tableName;
    }

    /**
     * 返回分片键字段名，例如 order_id。
     */
    public String shardKeyName() {
        return shardKeyName;
    }

    /**
     * 返回分片键字段值，例如 order-10001。
     */
    public Object shardKeyValue() {
        return shardKeyValue;
    }

    /**
     * 返回不可变扩展属性集合。
     */
    public Map<String, Object> attributes() {
        return attributes;
    }

    /**
     * 按名称读取扩展属性。
     *
     * @param name 属性名
     * @return 属性值；不存在时返回 null
     */
    public Object attribute(String name) {
        return attributes.get(name);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static Map<String, Object> immutableCopy(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StorageRoute that)) {
            return false;
        }
        return mode == that.mode
                && Objects.equals(routeName, that.routeName)
                && Objects.equals(dataSourceKey, that.dataSourceKey)
                && Objects.equals(tableName, that.tableName)
                && Objects.equals(shardKeyName, that.shardKeyName)
                && Objects.equals(shardKeyValue, that.shardKeyValue)
                && Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, routeName, dataSourceKey, tableName, shardKeyName, shardKeyValue, attributes);
    }

    @Override
    public String toString() {
        return "StorageRoute{"
                + "mode=" + mode
                + ", routeName='" + routeName + '\''
                + ", dataSourceKey='" + dataSourceKey + '\''
                + ", tableName='" + tableName + '\''
                + ", shardKeyName='" + shardKeyName + '\''
                + ", shardKeyValue=" + shardKeyValue
                + ", attributes=" + attributes
                + '}';
    }

    /**
     * StorageRoute 构造器。
     *
     * <p>Builder 只负责收集字段；字段合法性和空白字符串规整在 StorageRoute 构造函数里完成。
     * 第一版没有强制 dataSourceKey/tableName 必填，是为了兼容 ShardingSphere-JDBC / PROXY 等逻辑路由模式。</p>
     */
    public static final class Builder {

        private StorageRouteMode mode = StorageRouteMode.DIRECT_DATASOURCE;
        private String routeName;
        private String dataSourceKey;
        private String tableName;
        private String shardKeyName;
        private Object shardKeyValue;
        private Map<String, Object> attributes = Collections.emptyMap();

        private Builder() {
        }

        /**
         * 设置路由模式，默认是 DIRECT_DATASOURCE。
         */
        public Builder mode(StorageRouteMode mode) {
            this.mode = mode;
            return this;
        }

        /**
         * 设置路由场景名称，例如 order-create。它主要用于日志、指标、排障和规则区分。
         */
        public Builder routeName(String routeName) {
            this.routeName = routeName;
            return this;
        }

        /**
         * 设置数据源标识，例如 order-db-1。
         */
        public Builder dataSourceKey(String dataSourceKey) {
            this.dataSourceKey = dataSourceKey;
            return this;
        }

        /**
         * 设置表名，例如 business_order_017 或 business_order。
         */
        public Builder tableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        /**
         * 设置分片键字段名，例如 order_id。
         */
        public Builder shardKeyName(String shardKeyName) {
            this.shardKeyName = shardKeyName;
            return this;
        }

        /**
         * 设置分片键字段值，例如 order-10001。
         */
        public Builder shardKeyValue(Object shardKeyValue) {
            this.shardKeyValue = shardKeyValue;
            return this;
        }

        /**
         * 批量设置扩展属性。
         */
        public Builder attributes(Map<String, Object> attributes) {
            this.attributes = attributes == null ? Collections.emptyMap() : attributes;
            return this;
        }

        /**
         * 增加单个扩展属性。
         */
        public Builder attribute(String name, Object value) {
            if (this.attributes.isEmpty()) {
                this.attributes = new LinkedHashMap<>();
            } else if (!(this.attributes instanceof LinkedHashMap)) {
                this.attributes = new LinkedHashMap<>(this.attributes);
            }
            this.attributes.put(Objects.requireNonNull(name, "attribute name must not be null"), value);
            return this;
        }

        public StorageRoute build() {
            return new StorageRoute(this);
        }
    }
}
