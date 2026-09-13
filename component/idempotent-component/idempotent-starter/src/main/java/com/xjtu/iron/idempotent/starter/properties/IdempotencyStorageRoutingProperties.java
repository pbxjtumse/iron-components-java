package com.xjtu.iron.idempotent.starter.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Idempotency JDBC Storage 的分库分表集成配置。
 *
 * <p>该配置和 {@link IdempotencyProperties.Jdbc#getTableName()} 故意分离：</p>
 * <ul>
 *     <li>{@code xjtu.iron.idempotent.jdbc.table-name}：固定单库单表模式下的完整物理表名；</li>
 *     <li>{@code logical-table}：传给 Storage Routing 的逻辑表族名称，只作为路由语义元数据；</li>
 *     <li>{@code table-prefix}：有 shardInfo 时把同一业务 shard 映射为幂等物理表的前缀。</li>
 * </ul>
 *
 * <p>三个值默认相同只是为了零配置兼容默认表名，不代表它们在架构上是同一个概念。</p>
 */
@ConfigurationProperties(prefix = "xjtu.iron.idempotent.jdbc.routing")
public class IdempotencyStorageRoutingProperties {

    /** 是否允许 Starter 在 Storage Routing 可用时启用 JDBC 路由集成。 */
    private boolean enabled = true;

    /** Storage Routing RouteContext 使用的逻辑表名。 */
    private String logicalTable = "iron_idempotency_record";

    /** 按 ShardRouteInfo 映射幂等物理表时使用的表前缀。 */
    private String tablePrefix = "iron_idempotency_record";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getLogicalTable() {
        return logicalTable;
    }

    public void setLogicalTable(String logicalTable) {
        this.logicalTable = logicalTable;
    }

    public String getTablePrefix() {
        return tablePrefix;
    }

    public void setTablePrefix(String tablePrefix) {
        this.tablePrefix = tablePrefix;
    }
}
