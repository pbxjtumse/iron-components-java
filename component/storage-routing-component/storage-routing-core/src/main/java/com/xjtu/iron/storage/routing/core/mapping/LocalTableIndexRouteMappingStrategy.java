package com.xjtu.iron.storage.routing.core.mapping;

import com.xjtu.iron.storage.routing.api.PhysicalStorageLocation;
import com.xjtu.iron.storage.routing.api.ShardRouteInfo;
import com.xjtu.iron.storage.routing.api.mapping.RouteMappingStrategy;

/**
 * 库内表编号策略。
 *
 * <p>例如：</p>
 * <pre>
 * db_00.order_00 ~ order_09
 * db_01.order_00 ~ order_09
 * </pre>
 */
public final class LocalTableIndexRouteMappingStrategy implements RouteMappingStrategy {

    private final String dataSourcePrefix;
    private final String tablePrefix;
    private final int tableIndexWidth;

    public LocalTableIndexRouteMappingStrategy(
            String dataSourcePrefix,
            String tablePrefix,
            int tableIndexWidth
    ) {
        this.dataSourcePrefix = dataSourcePrefix;
        this.tablePrefix = tablePrefix;
        this.tableIndexWidth = tableIndexWidth;
    }

    @Override
    public PhysicalStorageLocation map(ShardRouteInfo shardRouteInfo) {
        return PhysicalStorageLocation.of(
                dataSourcePrefix + format(shardRouteInfo.databaseIndex()),
                tablePrefix + "_" + format(shardRouteInfo.localTableIndex())
        );
    }

    private String format(int value) {
        return String.format("%0" + tableIndexWidth + "d", value);
    }
}
