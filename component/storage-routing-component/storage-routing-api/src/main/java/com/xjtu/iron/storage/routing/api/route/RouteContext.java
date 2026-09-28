package com.xjtu.iron.storage.routing.api.route;

import com.xjtu.iron.storage.routing.api.exception.StorageRoutingException;
import com.xjtu.iron.storage.routing.api.key.CompositeShardKey;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 一次路由的输入数据，不是 ThreadLocal 容器。
 *
 * <p>承载场景、分片键和扩展属性；logicalTable 也在此处表达，保持表族元数据的一份来源。
 * 分片计算只接收 CompositeShardKey，物理映射使用已配置的规则，均不从 attributes 偷读标准分片字段。</p>
 *
 * <p>固定直连可不提供 shardKey；需要计算分片的解析器必须调用 requireShardKey()。
 * attributes 是 Map 结构快照，值不会深复制，调用方应保证扩展值稳定。</p>

 * <p><b>流程阅读编号：数据模型 R0：路由输入。</b>编号按 I（幂等）、R（路由）、D（数据访问）分组，不表示所有分支均依次执行。</p>
 * <ul>
 *     <li>1. shardKey 提供分片输入，logicalTable 表达逻辑表族，属性承载附加元数据。</li>
 *     <li>2. 输入本身不是物理落点；计算后得到的 StorageRoute 才同时携带分片结果和位置。</li>
 * </ul>
 */
public final class RouteContext {

    private final String routeName;
    private final String logicalTable;
    private final CompositeShardKey shardKey;
    private final Map<String, Object> attributes;

    private RouteContext(Builder builder) {
        this.routeName = normalize(builder.routeName);
        this.logicalTable = normalize(builder.logicalTable);
        this.shardKey = builder.shardKey;
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(builder.attributes));
    }

    public static Builder builder() {
        return new Builder();
    }

    public String routeName() {
        return routeName;
    }

    public String logicalTable() {
        return logicalTable;
    }

    public CompositeShardKey shardKey() {
        return shardKey;
    }

    public CompositeShardKey requireShardKey() {
        if (shardKey == null) {
            throw new StorageRoutingException("shardKey is required for shard resolution");
        }
        return shardKey;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }

    public Object attribute(String name) {
        return attributes.get(name);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static final class Builder {

        private String routeName;
        private String logicalTable;
        private CompositeShardKey shardKey;
        private Map<String, Object> attributes = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder routeName(String routeName) {
            this.routeName = routeName;
            return this;
        }

        public Builder logicalTable(String logicalTable) {
            this.logicalTable = logicalTable;
            return this;
        }

        public Builder shardKey(CompositeShardKey shardKey) {
            this.shardKey = shardKey;
            return this;
        }

        public Builder attributes(Map<String, Object> attributes) {
            this.attributes = new LinkedHashMap<>();
            if (attributes != null) {
                attributes.forEach(this::attribute);
            }
            return this;
        }

        public Builder attribute(String name, Object value) {
            this.attributes.put(Objects.requireNonNull(name, "attribute name must not be null"), value);
            return this;
        }

        public RouteContext build() {
            return new RouteContext(this);
        }
    }

    @Override
    public String toString() {
        return "RouteContext{routeName='" + routeName + "', logicalTable='" + logicalTable + "', shardFields="
                + (shardKey == null ? 0 : shardKey.size()) + '}';
    }
}
