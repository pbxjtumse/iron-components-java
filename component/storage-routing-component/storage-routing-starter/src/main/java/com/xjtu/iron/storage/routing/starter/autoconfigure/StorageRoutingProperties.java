package com.xjtu.iron.storage.routing.starter.autoconfigure;

import com.xjtu.iron.storage.routing.api.mapping.TableIndexMode;
import com.xjtu.iron.storage.routing.api.route.StorageRouteMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Storage Routing 自动装配配置。
 *
 * <p>默认只装配上下文和 relational bridge。默认解析器需要显式启用，因为库表数量、
 * 数据源前缀和表前缀都属于业务路由规则，不应在没有配置时猜测。</p>
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
        private String dataSourcePrefix = "db_";
        private String tablePrefix = "order";
        private int databaseCount = 1;
        private int tablesPerDatabase = 1;
        private int dataSourceIndexWidth = 2;
        private int tableIndexWidth = 2;
        private TableIndexMode tableIndexMode = TableIndexMode.GLOBAL_TABLE_INDEX;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

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
