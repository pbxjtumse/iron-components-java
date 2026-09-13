package com.xjtu.iron.relational.core.connection;

import com.xjtu.iron.relational.api.exception.RelationalAccessException;
import com.xjtu.iron.relational.api.exception.RelationalFailureType;
import com.xjtu.iron.relational.spi.connection.DataSourceResolver;
import com.xjtu.iron.relational.spi.execution.SqlExecutionContext;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 基于 dataSourceKey 的多数据源解析器。
 *
 * <p>该类解决的是 {@code dataSourceKey -> DataSource} 的基础设施映射，不负责分库分表算法。
 * 如果上层需要按照 userId / orderId / merchantId 计算分片，应先由 Sharding Component 或 Storage Adapter
 * 计算出目标 {@code dataSourceKey} 和物理表名，再把 {@code dataSourceKey} 放入 SqlRoute。</p>
 *
 * <p>默认路由会返回 defaultDataSource；命名路由会从 routedDataSources 中查找。未知命名路由必须 fail-fast，
 * 避免写错 key 后悄悄落到默认库。</p>
 */
public final class RoutingDataSourceResolver implements DataSourceResolver {

    /** 默认数据源，SqlRoute.defaultRoute() 会落到这里。 */
    private final DataSource defaultDataSource;

    /** 命名数据源映射，例如 infra-db、order-db、archive-db。 */
    private final Map<String, DataSource> routedDataSources;

    public RoutingDataSourceResolver(
            DataSource defaultDataSource,
            Map<String, ? extends DataSource> routedDataSources
    ) {
        this.defaultDataSource = Objects.requireNonNull(defaultDataSource, "defaultDataSource");
        this.routedDataSources = normalize(routedDataSources);
    }

    public static RoutingDataSourceResolver of(
            DataSource defaultDataSource,
            Map<String, ? extends DataSource> routedDataSources
    ) {
        return new RoutingDataSourceResolver(defaultDataSource, routedDataSources);
    }

    @Override
    public DataSource resolve(SqlExecutionContext context) {
        Objects.requireNonNull(context, "context");

        if (context.route().isDefaultRoute()) {
            return defaultDataSource;
        }

        String dataSourceKey = context.route().dataSourceKey();
        DataSource dataSource = routedDataSources.get(dataSourceKey);
        if (dataSource != null) {
            return dataSource;
        }

        throw new RelationalAccessException(
                RelationalFailureType.DATA_SOURCE_ROUTING_ERROR,
                context.operationName(),
                null,
                null,
                "RoutingDataSourceResolver cannot resolve dataSourceKey=" + dataSourceKey
                        + ", knownDataSourceKeys=" + routedDataSources.keySet(),
                null
        );
    }

    public DataSource defaultDataSource() {
        return defaultDataSource;
    }

    public Map<String, DataSource> routedDataSources() {
        return routedDataSources;
    }

    private static Map<String, DataSource> normalize(Map<String, ? extends DataSource> routedDataSources) {
        if (routedDataSources == null || routedDataSources.isEmpty()) {
            return Map.of();
        }

        Map<String, DataSource> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, ? extends DataSource> entry : routedDataSources.entrySet()) {
            String key = normalizeKey(entry.getKey());
            DataSource dataSource = Objects.requireNonNull(entry.getValue(), "dataSource for key=" + key);
            DataSource previous = normalized.put(key, dataSource);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate dataSourceKey=" + key);
            }
        }
        return Collections.unmodifiableMap(normalized);
    }

    private static String normalizeKey(String dataSourceKey) {
        if (dataSourceKey == null || dataSourceKey.isBlank()) {
            throw new IllegalArgumentException("dataSourceKey must not be blank");
        }
        return dataSourceKey.trim();
    }
}
