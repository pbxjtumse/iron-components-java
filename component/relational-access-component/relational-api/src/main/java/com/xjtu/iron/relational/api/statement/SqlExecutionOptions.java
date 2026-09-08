package com.xjtu.iron.relational.api.statement;

import java.time.Duration;
import java.util.Objects;

/**
 * 单次 SQL 执行选项。
 *
 * <p>null 表示使用组件默认配置。这里不描述事务超时和业务超时，只描述 JDBC Statement
 * 层面的执行提示。</p>
 */
public final class SqlExecutionOptions {

    private static final SqlExecutionOptions DEFAULTS = new SqlExecutionOptions(null, null, null);

    /** 映射到 PreparedStatement.setQueryTimeout，控制 JDBC Statement 查询超时。 */
    private final Duration timeout;

    /** 映射到 PreparedStatement.setFetchSize，给驱动的结果集抓取批次建议。 */
    private final Integer fetchSize;

    /** 映射到 PreparedStatement.setMaxRows，限制 ResultSet 最大返回行数。 */
    private final Integer maxRows;

    public SqlExecutionOptions(
            Duration timeout,
            Integer fetchSize,
            Integer maxRows
    ) {
        this.timeout = timeout;
        this.fetchSize = fetchSize;
        this.maxRows = maxRows;
    }

    public static SqlExecutionOptions defaults() {
        return DEFAULTS;
    }

    public static SqlExecutionOptions timeout(Duration timeout) {
        return builder().timeout(timeout).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Duration timeout() {
        return timeout;
    }

    public Integer fetchSize() {
        return fetchSize;
    }

    public Integer maxRows() {
        return maxRows;
    }

    public SqlExecutionOptions withTimeout(Duration newTimeout) {
        return new SqlExecutionOptions(newTimeout, fetchSize, maxRows);
    }

    public SqlExecutionOptions withFetchSize(Integer newFetchSize) {
        return new SqlExecutionOptions(timeout, newFetchSize, maxRows);
    }

    public SqlExecutionOptions withMaxRows(Integer newMaxRows) {
        return new SqlExecutionOptions(timeout, fetchSize, newMaxRows);
    }

    @Override
    public String toString() {
        return "SqlExecutionOptions{" +
                "timeout=" + timeout +
                ", fetchSize=" + fetchSize +
                ", maxRows=" + maxRows +
                '}';
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof SqlExecutionOptions that)) {
            return false;
        }
        return Objects.equals(timeout, that.timeout)
                && Objects.equals(fetchSize, that.fetchSize)
                && Objects.equals(maxRows, that.maxRows);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timeout, fetchSize, maxRows);
    }

    public static final class Builder {

        private Duration timeout;
        private Integer fetchSize;
        private Integer maxRows;

        private Builder() {
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder fetchSize(Integer fetchSize) {
            this.fetchSize = fetchSize;
            return this;
        }

        public Builder maxRows(Integer maxRows) {
            this.maxRows = maxRows;
            return this;
        }

        public SqlExecutionOptions build() {
            return new SqlExecutionOptions(timeout, fetchSize, maxRows);
        }
    }
}
