package com.xjtu.iron.relational.mybatis;

import com.xjtu.iron.transaction.api.execution.TransactionExecutor;
import com.xjtu.iron.transaction.api.execution.TransactionExecutorResolver;
import com.xjtu.iron.transaction.core.executor.RoutingTransactionExecutorResolver;

import javax.sql.DataSource;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Direct 模式共用的 MyBatis 与事务资源注册表。 */
public final class DirectMyBatisResourceRegistry {

    /** dataSourceKey 到完整资源的不可变映射。 */
    private final Map<String, DirectMyBatisResource> resources;

    /** dataSourceKey 到 MyBatis Access 的解析器。 */
    private final MyBatisAccessResolver myBatisAccessResolver;

    /** dataSourceKey 到事务执行器的解析器。 */
    private final TransactionExecutorResolver transactionExecutorResolver;

    public DirectMyBatisResourceRegistry(Collection<DirectMyBatisResource> resources) {
        Objects.requireNonNull(resources, "resources must not be null");
        if (resources.isEmpty()) {
            throw new IllegalArgumentException("direct resources must not be empty");
        }

        Map<String, DirectMyBatisResource> resourceMap = new LinkedHashMap<>();
        Map<DataSource, String> dataSourceIdentities = new IdentityHashMap<>();
        Map<String, MyBatisAccess> accesses = new LinkedHashMap<>();
        Map<String, TransactionExecutor> executors = new LinkedHashMap<>();
        for (DirectMyBatisResource resource : resources) {
            Objects.requireNonNull(resource, "resource must not be null");
            String key = resource.getDataSourceKey();
            if (resourceMap.put(key, resource) != null) {
                throw new IllegalArgumentException("duplicate dataSourceKey=" + key);
            }
            if (dataSourceIdentities.put(resource.getDataSource(), key) != null) {
                throw new IllegalArgumentException("DataSource registered under multiple keys: " + key);
            }
            accesses.put(key, resource.getMyBatisAccess());
            executors.put(key, resource.getTransactionExecutor());
        }
        this.resources = Map.copyOf(resourceMap);
        this.myBatisAccessResolver = new RoutingMyBatisAccessResolver(null, accesses);
        this.transactionExecutorResolver = new RoutingTransactionExecutorResolver(executors);
    }

    /** 从 Spring DataSource Bean Map 创建注册表，不创建也不关闭连接池。 */
    public static DirectMyBatisResourceRegistry fromDataSources(
            Map<String, ? extends DataSource> dataSources
    ) {
        Objects.requireNonNull(dataSources, "dataSources must not be null");
        return new DirectMyBatisResourceRegistry(
                dataSources.entrySet().stream()
                        .map(entry -> new DirectMyBatisResource(entry.getKey(), entry.getValue()))
                        .toList()
        );
    }

    /** 要求注册表中存在指定物理数据源。 */
    public DirectMyBatisResource require(String dataSourceKey) {
        String key = dataSourceKey == null ? null : dataSourceKey.trim();
        DirectMyBatisResource resource = key == null ? null : resources.get(key);
        if (resource == null) {
            throw new IllegalArgumentException("unknown direct dataSourceKey=" + dataSourceKey);
        }
        return resource;
    }

    /** 返回只读的物理数据源视图。 */
    public Map<String, DataSource> getDataSources() {
        Map<String, DataSource> result = new LinkedHashMap<>();
        resources.forEach((key, resource) -> result.put(key, resource.getDataSource()));
        return Map.copyOf(result);
    }

    public MyBatisAccessResolver getMyBatisAccessResolver() {
        return myBatisAccessResolver;
    }

    public TransactionExecutorResolver getTransactionExecutorResolver() {
        return transactionExecutorResolver;
    }
}
