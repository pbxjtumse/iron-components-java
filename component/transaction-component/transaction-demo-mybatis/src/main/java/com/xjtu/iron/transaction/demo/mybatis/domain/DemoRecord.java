package com.xjtu.iron.transaction.demo.mybatis.domain;

/**
 * MyBatis Demo 最小数据对象。
 */
public final class DemoRecord {

    private final long id;
    private final String name;

    public DemoRecord(long id, String name) {
        this.id = id;
        this.name = name;
    }

    public long getId() { return id; }
    public String getName() { return name; }
}
