package com.xjtu.iron.storage.routing.starter.autoconfigure;

import com.xjtu.iron.storage.routing.api.mapping.TableIndexMode;
import com.xjtu.iron.storage.routing.api.route.StorageRouteMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Storage Routing 自动装配配置。
 *
 * <p>默认只装配上下文和 relational bridge。Direct Resolver 需要显式启用，并通过 type 区分
 * 固定直连（FIXED）和按 key 分片直连（HASH），避免把执行模式与路由算法混为一谈。</p>
 */
@ConfigurationProperties(prefix = "xjtu.iron.storage-routing")
public class StorageRoutingProperties {

    private StorageRouteMode mode = StorageRouteMode.DIRECT_DATASOURCE;
    private final Resolver resolver = new Resolver();
    private final ShardingSphereJdbc shardingSphereJdbc = new ShardingSphereJdbc();
    private final ShardingSphereProxy shardingSphereProxy = new ShardingSphereProxy();

    public StorageRouteMode getMode() {
        return mode;
    }

    public void setMode(StorageRouteMode mode) {
        this.mode = mode;
    }

    public Resolver getResolver() {
        return resolver;
    }

    public ShardingSphereJdbc getShardingSphereJdbc() {
        return shardingSphereJdbc;
    }

    public ShardingSphereProxy getShardingSphereProxy() {
        return shardingSphereProxy;
    }

    public static final class ShardingSphereJdbc {

        /** Relational Access 中 ShardingSphere 逻辑 DataSource 的键；空值表示默认 DataSource。 */
        private String dataSourceKey;

        public String getDataSourceKey() {
            return dataSourceKey;
        }

        public void setDataSourceKey(String dataSourceKey) {
            this.dataSourceKey = dataSourceKey;
        }
    }

    public static final class ShardingSphereProxy {

        /** Relational Access 中连接 ShardingSphere-Proxy 的 DataSource 键；空值表示默认 DataSource。 */
        private String dataSourceKey;

        public String getDataSourceKey() {
            return dataSourceKey;
        }

        public void setDataSourceKey(String dataSourceKey) {
            this.dataSourceKey = dataSourceKey;
        }
    }

    public static final class Resolver {

        private boolean enabled;
        private ResolverType type = ResolverType.HASH;
        private final Fixed fixed = new Fixed();
        private final Hash hash = new Hash();

        public ResolverType getType() {
            return type;
        }

        public void setType(ResolverType type) {
            this.type = type;
        }

        public Fixed getFixed() {
            return fixed;
        }

        public Hash getHash() {
            return hash;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public enum ResolverType {
        FIXED,
        HASH
    }

    public static final class Fixed {

        private String dataSourceKey;
        private String tableName;

        public String getDataSourceKey() {
            return dataSourceKey;
        }

        public void setDataSourceKey(String dataSourceKey) {
            this.dataSourceKey = dataSourceKey;
        }

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }
    }

    public static final class Hash {

        private String dataSourcePrefix = "db_";
        private String tablePrefix = "order";
        private int databaseCount = 1;
        private int tablesPerDatabase = 1;
        private int dataSourceIndexWidth = 2;
        private int tableIndexWidth = 2;
        private TableIndexMode tableIndexMode = TableIndexMode.GLOBAL_TABLE_INDEX;

        public String getDataSourcePrefix() {
            return dataSourcePrefix;
        }

        public void setDataSourcePrefix(String dataSourcePrefix) {
            this.dataSourcePrefix = dataSourcePrefix;
        }

        public String getTablePrefix() {
            return tablePrefix;
        }

        public void setTablePrefix(String tablePrefix) {
            this.tablePrefix = tablePrefix;
        }

        public int getDatabaseCount() {
            return databaseCount;
        }

        public void setDatabaseCount(int databaseCount) {
            this.databaseCount = databaseCount;
        }

        public int getTablesPerDatabase() {
            return tablesPerDatabase;
        }

        public void setTablesPerDatabase(int tablesPerDatabase) {
            this.tablesPerDatabase = tablesPerDatabase;
        }

        public int getDataSourceIndexWidth() {
            return dataSourceIndexWidth;
        }

        public void setDataSourceIndexWidth(int dataSourceIndexWidth) {
            this.dataSourceIndexWidth = dataSourceIndexWidth;
        }

        public int getTableIndexWidth() {
            return tableIndexWidth;
        }

        public void setTableIndexWidth(int tableIndexWidth) {
            this.tableIndexWidth = tableIndexWidth;
        }

        public TableIndexMode getTableIndexMode() {
            return tableIndexMode;
        }

        public void setTableIndexMode(TableIndexMode tableIndexMode) {
            this.tableIndexMode = tableIndexMode;
        }
    }
}
