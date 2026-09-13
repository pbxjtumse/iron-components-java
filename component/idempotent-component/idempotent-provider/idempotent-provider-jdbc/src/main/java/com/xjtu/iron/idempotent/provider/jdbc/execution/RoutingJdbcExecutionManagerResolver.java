package com.xjtu.iron.idempotent.provider.jdbc.execution;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 多 DataSource 场景的 JdbcExecutionManagerResolver。
 *
 * <p>Storage Routing 先解析得到 dataSourceKey，本类只做 dataSourceKey -> JdbcExecutionManager 映射。
 * 它不计算 hash、不决定表名，也不参与幂等状态判断。</p>
 */
public final class RoutingJdbcExecutionManagerResolver implements JdbcExecutionManagerResolver {

    private final JdbcExecutionManager defaultManager;
    private final Map<String, JdbcExecutionManager> managers;

    public RoutingJdbcExecutionManagerResolver(
            JdbcExecutionManager defaultManager,
            Map<String, ? extends JdbcExecutionManager> managers
    ) {
        this.defaultManager = defaultManager;
        this.managers = normalize(managers);
    }

    @Override
    public JdbcExecutionManager resolve(String dataSourceKey) {
        String key = normalizeKey(dataSourceKey);
        if (key == null) {
            if (defaultManager == null) {
                throw new IllegalArgumentException("no default JdbcExecutionManager configured");
            }
            return defaultManager;
        }

        JdbcExecutionManager manager = managers.get(key);
        if (manager == null) {
            throw new IllegalArgumentException(
                    "JdbcExecutionManager not found for dataSourceKey=" + key + ", knownDataSourceKeys=" + managers.keySet());
        }
        return manager;
    }

    @Override
    public boolean supportsCurrentTransactionParticipation() {
        if (defaultManager != null && !defaultManager.supportsCurrentTransactionParticipation()) {
            return false;
        }
        return managers.values().stream().allMatch(JdbcExecutionManager::supportsCurrentTransactionParticipation);
    }

    private static Map<String, JdbcExecutionManager> normalize(Map<String, ? extends JdbcExecutionManager> source) {
        if (source == null || source.isEmpty()) return Map.of();
        Map<String, JdbcExecutionManager> result = new LinkedHashMap<>();
        for (Map.Entry<String, ? extends JdbcExecutionManager> entry : source.entrySet()) {
            String key = normalizeKey(entry.getKey());
            if (key == null) throw new IllegalArgumentException("dataSourceKey must not be blank");
            JdbcExecutionManager previous = result.put(key, Objects.requireNonNull(entry.getValue(), "manager for " + key));
            if (previous != null) throw new IllegalArgumentException("duplicate dataSourceKey=" + key);
        }
        return Map.copyOf(result);
    }

    private static String normalizeKey(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
