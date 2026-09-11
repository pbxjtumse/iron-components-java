package com.xjtu.iron.storage.routing.api;

import java.util.Map;
import java.util.Objects;

/**
 * 旧单字段路由请求的兼容适配器。
 *
 * <p>内部仅保存一份 RouteContext，旧入口在构造时将 name/value 转成单元素 CompositeShardKey。
 * 新代码直接构造 RouteContext；复合键无需也不能压回这个单字段模型。</p>
 *
 * @deprecated 使用 RouteContext。
 */
@Deprecated
public final class StorageRouteRequest {

    private final RouteContext context;

    private StorageRouteRequest(Builder builder) {
        if (builder.logicalTable == null || builder.logicalTable.isBlank()) {
            throw new StorageRoutingException("logicalTable must not be blank");
        }
        this.context = builder.contextBuilder.logicalTable(builder.logicalTable)
                .shardKey(CompositeShardKey.of(ShardKey.of(builder.shardKeyName,
                        Objects.requireNonNull(builder.shardKeyValue, "shardKeyValue must not be null"))))
                .build();
    }

    public static StorageRouteRequest of(String logicalTable, String shardKeyName, Object shardKeyValue) {
        return builder().logicalTable(logicalTable).shardKeyName(shardKeyName).shardKeyValue(shardKeyValue).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public RouteContext toContext() {
        return context;
    }

    public String scene() {
        return context.routeName();
    }

    public String logicalTable() {
        return context.logicalTable();
    }

    public String shardKeyName() {
        return context.requireShardKey().singleKey().name();
    }

    public Object shardKeyValue() {
        return context.requireShardKey().singleKey().value().value();
    }

    public Map<String, Object> attributes() {
        return context.attributes();
    }

    public Object attribute(String name) {
        return context.attribute(name);
    }

    public static final class Builder {

        private final RouteContext.Builder contextBuilder = RouteContext.builder();
        private String logicalTable;
        private String shardKeyName;
        private Object shardKeyValue;

        private Builder() {
        }

        public Builder scene(String scene) {
            contextBuilder.routeName(scene);
            return this;
        }

        public Builder logicalTable(String logicalTable) {
            this.logicalTable = logicalTable;
            return this;
        }

        public Builder shardKeyName(String shardKeyName) {
            this.shardKeyName = shardKeyName;
            return this;
        }

        public Builder shardKeyValue(Object shardKeyValue) {
            this.shardKeyValue = shardKeyValue;
            return this;
        }

        public Builder attributes(Map<String, Object> attributes) {
            contextBuilder.attributes(attributes);
            return this;
        }

        public Builder attribute(String name, Object value) {
            contextBuilder.attribute(name, value);
            return this;
        }

        public StorageRouteRequest build() {
            return new StorageRouteRequest(this);
        }
    }

    @Override
    public String toString() {
        return "StorageRouteRequest{context=" + context + '}';
    }
}
