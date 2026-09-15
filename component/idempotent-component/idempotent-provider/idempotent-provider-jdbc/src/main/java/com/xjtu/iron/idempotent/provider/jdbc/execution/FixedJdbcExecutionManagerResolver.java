package com.xjtu.iron.idempotent.provider.jdbc.execution;

import java.util.Objects;

/**
 * 单 DataSource 场景的 JdbcExecutionManagerResolver。
 *
 * <p>默认模式只接受 null dataSourceKey；如果明确声明 acceptedDataSourceKey，则同时接受该命名路由。
 * 未知命名路由必须 fail-fast，不能悄悄落到默认 DataSource，否则分库分表场景可能把幂等记录写错库。</p>
 */
public final class FixedJdbcExecutionManagerResolver implements JdbcExecutionManagerResolver {

    private final JdbcExecutionManager manager;
    private final String acceptedDataSourceKey;

    public FixedJdbcExecutionManagerResolver(JdbcExecutionManager manager, String acceptedDataSourceKey) {
        this.manager = Objects.requireNonNull(manager, "manager must not be null");
        this.acceptedDataSourceKey = normalize(acceptedDataSourceKey);
    }

    public static FixedJdbcExecutionManagerResolver defaultDataSource(JdbcExecutionManager manager) {
        return new FixedJdbcExecutionManagerResolver(manager, null);
    }

    public static FixedJdbcExecutionManagerResolver named(JdbcExecutionManager manager, String dataSourceKey) {
        return new FixedJdbcExecutionManagerResolver(manager, dataSourceKey);
    }

    @Override
    public JdbcExecutionManager resolve(String dataSourceKey) {
        String requested = normalize(dataSourceKey);
        if (requested == null || Objects.equals(requested, acceptedDataSourceKey)) {
            return manager;
        }
        throw new IllegalArgumentException(
                "JdbcExecutionManager cannot resolve dataSourceKey=" + requested
                        + "; configure a routing JdbcExecutionManagerResolver instead of falling back to the default DataSource");
    }

    @Override
    public boolean supportsCurrentTransactionParticipation() {
        return manager.supportsCurrentTransactionParticipation();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
