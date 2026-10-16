package com.xjtu.iron.distributed.lock.starter.properties;

import com.xjtu.iron.distributed.lock.provider.mybatis.fencing.MyBatisFencingTokenConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** MyBatis sequence fencing token Provider 配置。 */
@ConfigurationProperties(prefix = "xjtu.iron.distributed-lock.fencing.mybatis")
public class MyBatisFencingTokenProperties {

    /** 是否启用 MyBatis sequence fencing token Provider。 */
    private boolean enabled;

    /** fencing token 技术表名，只允许简单 SQL 标识符。 */
    private String tableName = MyBatisFencingTokenConstants.DEFAULT_TABLE_NAME;

    /** 并发首次插入冲突时的最大重试次数。 */
    private int maxRetries = MyBatisFencingTokenConstants.DEFAULT_MAX_RETRIES;

    /** 是否由 Starter 自动执行建表脚本。 */
    private boolean initializeSchema;

    /** schema 平台：mysql 或 h2。 */
    private String schemaPlatform = "mysql";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public boolean isInitializeSchema() { return initializeSchema; }
    public void setInitializeSchema(boolean initializeSchema) { this.initializeSchema = initializeSchema; }
    public String getSchemaPlatform() { return schemaPlatform; }
    public void setSchemaPlatform(String schemaPlatform) { this.schemaPlatform = schemaPlatform; }
}
