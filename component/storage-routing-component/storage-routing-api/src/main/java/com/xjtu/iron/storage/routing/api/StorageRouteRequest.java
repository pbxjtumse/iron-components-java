package com.xjtu.iron.storage.routing.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 存储路由请求。
 *
 * <p>它是业务信息进入 StorageRouteResolver 前的统一载体。Resolver 不应该直接依赖 Order、Payment、Task 等业务对象，
 * 而是依赖这个稳定请求模型，从中读取 logicalTable、shardKeyName、shardKeyValue 等路由要素。</p>
 *
 * <p>使用示例：</p>
 *
 * <pre>{@code
 * StorageRouteRequest request = StorageRouteRequest.builder()
 *         .scene("order-create")
 *         .logicalTable("business_order")
 *         .shardKeyName("order_id")
 *         .shardKeyValue("order-10001")
 *         .attribute("idempotencyTable", "iron_idempotency_record")
 *         .build();
 * }</pre>
 *
 * <p>这段代码的含义是：我要处理一次下单场景，主业务逻辑表是 business_order，
 * 分片依据是 order_id=order-10001。至于最后落到 order-db-几、business_order_几，
 * 由 StorageRouteResolver 决定。</p>
 */
public final class StorageRouteRequest {

    /**
     * 业务场景名称。
     *
     * <p>scene 用来说明“这次为什么要路由”，不是库名，也不是表名。
     * 它常用于日志、指标、告警、规则匹配或排查问题。</p>
     *
     * <p>常见例子：</p>
     * <pre>{@code
     * order-create
     * order-pay
     * idempotency-try-acquire
     * outbox-save
     * task-recover
     * }</pre>
     */
    private final String scene;

    /**
     * 逻辑表名。
     *
     * <p>logicalTable 表达“业务上我要访问哪类表”，不是一定已经带后缀的物理表名。
     * 例如业务上是 business_order，最终物理表可能是 business_order_017。</p>
     *
     * <p>它的主要作用是给 Resolver 提供表族信息：同一个 shardKey 下，business_order、
     * iron_idempotency_record、iron_outbox 可能都需要根据各自 logicalTable 映射到同一库下的不同表。</p>
     */
    private final String logicalTable;

    /**
     * 分片字段名。
     *
     * <p>例如 order_id、user_id、merchant_id、tenant_id。它回答“用哪个字段计算路由”。</p>
     */
    private final String shardKeyName;

    /**
     * 分片字段值。
     *
     * <p>例如 order-10001。它回答“用哪个具体值计算路由”。HashStorageRouteResolver 会对这个值做稳定 hash，
     * 再计算 dataSourceIndex 和 tableIndex。</p>
     */
    private final Object shardKeyValue;

    /**
     * 扩展属性。
     *
     * <p>用于携带第一版暂时不适合固化成字段的信息，例如 tenantId、region、业务线、
     * 幂等表逻辑名、Outbox 表逻辑名等。Resolver 可以读取这些属性做更细的路由判断。</p>
     */
    private final Map<String, Object> attributes;

    private StorageRouteRequest(Builder builder) {
        this.scene = normalize(builder.scene);
        this.logicalTable = requireText(builder.logicalTable, "logicalTable must not be blank");
        this.shardKeyName = requireText(builder.shardKeyName, "shardKeyName must not be blank");
        this.shardKeyValue = Objects.requireNonNull(builder.shardKeyValue, "shardKeyValue must not be null");
        this.attributes = immutableCopy(builder.attributes);
    }

    /**
     * 创建最小路由请求。
     *
     * <p>适合只需要 logicalTable + shardKey 的场景。复杂场景建议使用 builder 设置 scene 和 attributes。</p>
     *
     * @param logicalTable 逻辑表名，例如 business_order
     * @param shardKeyName 分片键字段名，例如 order_id
     * @param shardKeyValue 分片键字段值，例如 order-10001
     * @return 路由请求
     */
    public static StorageRouteRequest of(String logicalTable, String shardKeyName, Object shardKeyValue) {
        return builder()
                .logicalTable(logicalTable)
                .shardKeyName(shardKeyName)
                .shardKeyValue(shardKeyValue)
                .build();
    }

    /**
     * 创建 Builder。
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 返回业务场景名称，例如 order-create。可以为空。
     */
    public String scene() {
        return scene;
    }

    /**
     * 返回逻辑表名，例如 business_order。
     */
    public String logicalTable() {
        return logicalTable;
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
     * 根据属性名读取扩展属性。
     *
     * @param name 属性名
     * @return 属性值；不存在时返回 null
     */
    public Object attribute(String name) {
        return attributes.get(name);
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

    private static Map<String, Object> immutableCopy(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    @Override
    public String toString() {
        return "StorageRouteRequest{"
                + "scene='" + scene + '\''
                + ", logicalTable='" + logicalTable + '\''
                + ", shardKeyName='" + shardKeyName + '\''
                + ", shardKeyValue=" + shardKeyValue
                + ", attributes=" + attributes
                + '}';
    }

    /**
     * StorageRouteRequest 构造器。
     *
     * <p>必填字段是 logicalTable、shardKeyName、shardKeyValue；scene 和 attributes 是可选字段。</p>
     */
    public static final class Builder {

        private String scene;
        private String logicalTable;
        private String shardKeyName;
        private Object shardKeyValue;
        private Map<String, Object> attributes = Collections.emptyMap();

        private Builder() {
        }

        /**
         * 设置业务场景名称，例如 order-create。
         */
        public Builder scene(String scene) {
            this.scene = scene;
            return this;
        }

        /**
         * 设置逻辑表名，例如 business_order。
         */
        public Builder logicalTable(String logicalTable) {
            this.logicalTable = logicalTable;
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

        public StorageRouteRequest build() {
            return new StorageRouteRequest(this);
        }
    }
}
