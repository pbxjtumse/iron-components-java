package com.xjtu.iron.idempotent.provider.jdbc.routing;

import java.util.Objects;

/**
 * JDBC 幂等存储最终执行位置。
 *
 * <p>该对象是 IdempotencyStorageContext 经过 Storage Routing 之后的“物理执行结果”：
 * dataSourceKey 决定 Relational/JDBC 最终使用哪个 DataSource，tableName 决定本次幂等 SQL 访问哪张物理表。</p>
 *
 * <p>它不包含 ownerToken/version/status 等幂等领域状态，也不负责计算分片。分片算法属于 Storage Routing，
 * 幂等状态机属于 Idempotency Repository，本类只承载二者之间已经解析完成的 JDBC 位置。</p>
 */
public final class IdempotencyJdbcRoute {

    /** 逻辑数据源标识；null 表示使用默认 DataSource。 */
    private final String dataSourceKey;

    /** 本次 SQL 应访问的最终物理表名。 */
    private final String tableName;

    public IdempotencyJdbcRoute(String dataSourceKey, String tableName) {
        this.dataSourceKey = normalize(dataSourceKey);
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("tableName must not be blank");
        }
        this.tableName = tableName.trim();
    }

    public static IdempotencyJdbcRoute of(String dataSourceKey, String tableName) {
        return new IdempotencyJdbcRoute(dataSourceKey, tableName);
    }

    public static IdempotencyJdbcRoute defaultDataSource(String tableName) {
        return new IdempotencyJdbcRoute(null, tableName);
    }

    public String dataSourceKey() { return dataSourceKey; }
    public String tableName() { return tableName; }
    public boolean usesDefaultDataSource() { return dataSourceKey == null; }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof IdempotencyJdbcRoute that)) return false;
        return Objects.equals(dataSourceKey, that.dataSourceKey) && tableName.equals(that.tableName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dataSourceKey, tableName);
    }

    @Override
    public String toString() {
        return "IdempotencyJdbcRoute{dataSourceKey='" + dataSourceKey + "', tableName='" + tableName + "'}";
    }
}
