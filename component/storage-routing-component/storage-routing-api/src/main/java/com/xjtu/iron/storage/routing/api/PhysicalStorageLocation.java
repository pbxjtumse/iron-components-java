package com.xjtu.iron.storage.routing.api;

import java.util.Objects;

/**
 * 物理存储位置。
 *
 * <p>StorageRoute 负责描述一次存储访问，而 PhysicalStorageLocation 专门描述最终落点。
 * 例如：order-db-05.order_56。</p>
 *
 * <p>未来接入 ShardingSphere-JDBC、MyCAT 或其他路由实现时，
 * 该对象可以由不同的 RouteMappingStrategy 生成。</p>
 */
public final class PhysicalStorageLocation {

    private final String dataSourceKey;

    private final String tableName;

    private PhysicalStorageLocation(String dataSourceKey, String tableName) {
        this.dataSourceKey = normalize(dataSourceKey);
        this.tableName = normalize(tableName);
    }

    public static PhysicalStorageLocation of(String dataSourceKey, String tableName) {
        return new PhysicalStorageLocation(dataSourceKey, tableName);
    }

    public String dataSourceKey() {
        return dataSourceKey;
    }

    public String tableName() {
        return tableName;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Override
    public String toString() {
        return "PhysicalStorageLocation{" +
                "dataSourceKey='" + dataSourceKey + '\'' +
                ", tableName='" + tableName + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PhysicalStorageLocation that)) {
            return false;
        }
        return Objects.equals(dataSourceKey, that.dataSourceKey)
                && Objects.equals(tableName, that.tableName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dataSourceKey, tableName);
    }
}
