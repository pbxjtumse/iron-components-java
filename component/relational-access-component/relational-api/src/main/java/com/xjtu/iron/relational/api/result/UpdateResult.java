package com.xjtu.iron.relational.api.result;

/**
 * INSERT / UPDATE / DELETE / UPSERT 等 JDBC executeUpdate() 路径的标准结果。
 */
public final class UpdateResult {

    /** JDBC 返回的受影响行数。 */
    private final long affectedRows;

    public UpdateResult(long affectedRows) {
        this.affectedRows = affectedRows;
    }

    public long affectedRows() {
        return affectedRows;
    }

    @Override
    public String toString() {
        return "UpdateResult{" +
                "affectedRows=" + affectedRows +
                '}';
    }
}
