package com.xjtu.iron.relational.api.result;

import java.util.Arrays;

/**
 * JDBC batch 的标准结果。
 */
public final class BatchResult {

    /** 与 PreparedStatement.executeBatch() 顺序对应的更新计数。 */
    private final int[] updateCounts;

    public BatchResult(int[] updateCounts) {
        this.updateCounts = updateCounts == null ? new int[0] : updateCounts.clone();
    }

    public int[] updateCounts() {
        return updateCounts.clone();
    }

    public int size() {
        return updateCounts.length;
    }

    @Override
    public String toString() {
        return "BatchResult{" +
                "updateCounts=" + Arrays.toString(updateCounts) +
                '}';
    }
}
