package com.xjtu.iron.idempotent.integration.transaction;

import com.xjtu.iron.idempotent.provider.jdbc.execution.JdbcExecutionManager;
import com.xjtu.iron.idempotent.provider.jdbc.execution.JdbcExecutionManagerResolver;
import com.xjtu.iron.idempotent.provider.jdbc.execution.RoutingJdbcExecutionManagerResolver;
import com.xjtu.iron.transaction.api.execution.TransactionExecutor;
import com.xjtu.iron.transaction.api.execution.TransactionExecutorResolver;
import com.xjtu.iron.transaction.core.executor.RoutingTransactionExecutorResolver;
import javax.sql.DataSource;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Direct 模式唯一资源目录；不创建连接池，不关闭调用方拥有的 DataSource。 */
public final class DirectStorageResourceRegistry {
    private final Map<String, DirectStorageResource> resources;
    private final JdbcExecutionManagerResolver jdbcResolver;
    private final TransactionExecutorResolver transactionResolver;

    public DirectStorageResourceRegistry(Collection<DirectStorageResource> resources) {
        Objects.requireNonNull(resources, "resources");
        if (resources.isEmpty()) throw new IllegalArgumentException("direct resources must not be empty");
        Map<String, DirectStorageResource> copy = new LinkedHashMap<>();
        Map<DataSource, String> identities = new IdentityHashMap<>();
        Map<String, JdbcExecutionManager> managers = new LinkedHashMap<>();
        Map<String, TransactionExecutor> executors = new LinkedHashMap<>();
        for (DirectStorageResource resource : resources) {
            Objects.requireNonNull(resource, "resource");
            String key = resource.dataSourceKey();
            if (copy.put(key, resource) != null) throw new IllegalArgumentException("duplicate dataSourceKey=" + key);
            if (identities.put(resource.dataSource(), key) != null) throw new IllegalArgumentException("DataSource registered under multiple keys: " + key);
            managers.put(key, resource.jdbcExecutionManager());
            executors.put(key, resource.transactionExecutor());
        }
        this.resources = Map.copyOf(copy);
        this.jdbcResolver = new RoutingJdbcExecutionManagerResolver(null, managers);
        this.transactionResolver = new RoutingTransactionExecutorResolver(executors);
    }

    public static DirectStorageResourceRegistry fromDataSources(Map<String, ? extends DataSource> dataSources) {
        Objects.requireNonNull(dataSources, "dataSources");
        return new DirectStorageResourceRegistry(dataSources.entrySet().stream().map(e -> new DirectStorageResource(e.getKey(), e.getValue())).toList());
    }

    public DirectStorageResource require(String dataSourceKey) {
        DirectStorageResource resource = dataSourceKey == null ? null : resources.get(dataSourceKey.trim());
        if (resource == null) throw new IllegalArgumentException("unknown direct dataSourceKey=" + dataSourceKey);
        return resource;
    }

    public Map<String, DataSource> dataSources() {
        Map<String, DataSource> result = new LinkedHashMap<>();
        resources.forEach((key, resource) -> result.put(key, resource.dataSource()));
        return Map.copyOf(result);
    }

    public JdbcExecutionManagerResolver jdbcExecutionManagerResolver() { return jdbcResolver; }
    public TransactionExecutorResolver transactionExecutorResolver() { return transactionResolver; }
}
