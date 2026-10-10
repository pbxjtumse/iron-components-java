package com.xjtu.iron.storage.routing.starter.autoconfigure;

import com.xjtu.iron.storage.routing.api.mapping.TableIndexMode;
import com.xjtu.iron.storage.routing.api.route.StorageRouteMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Storage Routing 自动装配配置。
 *
 * <p>默认只装配路由上下文。Direct Resolver 需要显式启用，并通过 type 区分
 * 固定直连（FIXED）和按 key 分片直连（HASH），避免把执行模式与路由算法混为一谈。</p>
 */
@ConfigurationProperties(prefix = "xjtu.iron.storage-routing")
public class StorageRoutingProperties {

    /** 全局路由接入模式。 */
    private StorageRouteMode mode = StorageRouteMode.DIRECT_DATASOURCE;

    /** 应用内路由解析配置。 */
    private final Resolver resolver = new Resolver();

    /** ShardingSphere-JDBC 接入配置。 */
    private final ShardingSphereJdbc shardingSphereJdbc = new ShardingSphereJdbc();

    /** ShardingSphere-Proxy 接入配置。 */
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

        /** MyBatis Access 中 ShardingSphere 逻辑 DataSource 的键；空值表示默认 DataSource。 */
        private String dataSourceKey;

        public String getDataSourceKey() {
            return dataSourceKey;
        }

        public void setDataSourceKey(String dataSourceKey) {
            this.dataSourceKey = dataSourceKey;
        }
    }

    public static final class ShardingSphereProxy {

        /** MyBatis Access 中连接 ShardingSphere-Proxy 的 DataSource 键；空值表示默认 DataSource。 */
        private String dataSourceKey;

        public String getDataSourceKey() {
            return dataSourceKey;
        }

        public void setDataSourceKey(String dataSourceKey) {
            this.dataSourceKey = dataSourceKey;
        }
    }

    public static final class Resolver {

        /** 是否启用 Starter 内置 Resolver。 */
        private boolean enabled;

        /** 固定位置或哈希分片解析方式。 */
        private ResolverType type = ResolverType.HASH;

        /** 固定直连参数。 */
        private final Fixed fixed = new Fixed();

        /** 哈希分片参数。 */
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

        /** 固定物理数据源标识。 */
        private String dataSourceKey;

        /** 固定物理表名。 */
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

        /** 物理数据源名称前缀。 */
        private String dataSourcePrefix = "db_";

        /** 物理表名称前缀。 */
        private String tablePrefix = "order";

        /** 物理数据库数量。 */
        private int databaseCount = 1;

        /** 每个物理数据库的表数量。 */
        private int tablesPerDatabase = 1;

        /** 数据源编号补零宽度。 */
        private int dataSourceIndexWidth = 2;

        /** 表编号补零宽度。 */
        private int tableIndexWidth = 2;

        /** 物理表使用全局编号还是库内编号。 */
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
