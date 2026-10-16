package com.xjtu.iron.idempotent.provider.mybatis.routing;

/** 幂等技术表一次访问的物理数据源和物理表。 */
public final class IdempotencyPhysicalRoute {

    /** 物理数据源标识；固定默认数据源模式允许为空。 */
    private final String dataSourceKey;

    /** 已通过白名单校验的物理表名。 */
    private final String tableName;

    public IdempotencyPhysicalRoute(String dataSourceKey, String tableName) {
        this.dataSourceKey = normalize(dataSourceKey);
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("tableName must not be blank");
        }
        this.tableName = tableName.trim();
    }

    public static IdempotencyPhysicalRoute of(String dataSourceKey, String tableName) {
        return new IdempotencyPhysicalRoute(dataSourceKey, tableName);
    }

    public String getDataSourceKey() {
        return dataSourceKey;
    }

    public String getTableName() {
        return tableName;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof IdempotencyPhysicalRoute that)) return false;
        return java.util.Objects.equals(dataSourceKey, that.dataSourceKey)
                && tableName.equals(that.tableName);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(dataSourceKey, tableName);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
