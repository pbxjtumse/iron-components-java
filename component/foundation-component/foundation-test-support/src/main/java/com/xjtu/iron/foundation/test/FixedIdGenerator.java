package com.xjtu.iron.foundation.test;

import com.xjtu.iron.foundation.id.api.IdGenerator;

/**
 * 固定 ID 生成器，适合断言明确 ID 的单元测试。
 */
public final class FixedIdGenerator implements IdGenerator {

    private final String value;

    public FixedIdGenerator(String value) { this.value = value; }

    @Override public String nextId() { return value; }
}
