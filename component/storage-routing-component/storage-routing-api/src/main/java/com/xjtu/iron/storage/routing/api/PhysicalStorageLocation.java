package com.xjtu.iron.storage.routing.api;

import java.util.Objects;

/**
 * 物理存储位置。
 *
 * <p>StorageRoute 负责描述一次存储访问，而 PhysicalStorageLocation 专门描述最终落点。
 * 例如：order-db-05.order_56。</p>
 *
 * <p>只表达已经解析出的物理位置，库与表必须同时存在。中间件尚未暴露真实落点时，
 * 应让 StorageRoute.physicalLocation() 为空，不能把逻辑表或代理入口伪装成物理位置。</p>
 */
public final class PhysicalStorageLocation {

    private final String dataSourceKey;

    private final String tableName;

    private PhysicalStorageLocation(String dataSourceKey, String tableName) {
        this.dataSourceKey = requireText(dataSourceKey, "dataSourceKey");
        this.tableName = requireText(tableName, "tableName");
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

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new StorageRoutingException(name + " must not be blank");
        }
        return value.trim();
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
