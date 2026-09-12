package com.xjtu.iron.storage.routing.core.mapping;

import com.xjtu.iron.storage.routing.api.TableIndexMode;
import com.xjtu.iron.storage.routing.api.mapping.RouteMappingStrategy;

import java.util.Objects;

/**
 * RouteMappingStrategy 工厂。
 *
 * <p>负责根据配置选择物理映射策略，不参与 shard 计算。</p>
 */
public final class RouteMappingStrategyFactory {

    private final String dataSourcePrefix;
    private final String tablePrefix;
    private final int dataSourceIndexWidth;
    private final int tableIndexWidth;

    public RouteMappingStrategyFactory(
            String dataSourcePrefix,
            String tablePrefix,
            int tableIndexWidth
    ) {
        this(dataSourcePrefix, tablePrefix, tableIndexWidth, tableIndexWidth);
    }

    public RouteMappingStrategyFactory(
            String dataSourcePrefix,
            String tablePrefix,
            int dataSourceIndexWidth,
            int tableIndexWidth
    ) {
        this.dataSourcePrefix = Objects.requireNonNull(dataSourcePrefix, "dataSourcePrefix");
        this.tablePrefix = Objects.requireNonNull(tablePrefix, "tablePrefix");
        this.dataSourceIndexWidth = dataSourceIndexWidth;
        this.tableIndexWidth = tableIndexWidth;
    }

    /**
     * 根据表编号模式创建映射策略。
     */
    public RouteMappingStrategy create(TableIndexMode mode) {
        Objects.requireNonNull(mode, "mode");
        return switch (mode) {
            case GLOBAL_TABLE_INDEX ->
                    new GlobalTableIndexRouteMappingStrategy(
                            dataSourcePrefix,
                            tablePrefix,
                            dataSourceIndexWidth,
                            tableIndexWidth
                    );
            case LOCAL_TABLE_INDEX ->
                    new LocalTableIndexRouteMappingStrategy(
                            dataSourcePrefix,
                            tablePrefix,
                            dataSourceIndexWidth,
                            tableIndexWidth
                    );
        };
    }
}
