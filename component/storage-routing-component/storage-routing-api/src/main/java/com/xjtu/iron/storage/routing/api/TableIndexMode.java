package com.xjtu.iron.storage.routing.api;

/**
 * 物理表编号策略。
 *
 * <p>用于描述分库分表场景中 tableName 如何生成。</p>
 */
public enum TableIndexMode {

    /**
     * 全局表编号。
     *
     * <p>例如：
     * db_00 -> order_00 ~ order_09
     * db_01 -> order_10 ~ order_19
     * </p>
     */
    GLOBAL_TABLE_INDEX,

    /**
     * 库内表编号。
     *
     * <p>例如：
     * db_00 -> order_00 ~ order_09
     * db_01 -> order_00 ~ order_09
     * </p>
     */
    LOCAL_TABLE_INDEX
}
