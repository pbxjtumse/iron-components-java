package com.xjtu.iron.foundation.test;

import com.xjtu.iron.foundation.id.api.IdGenerator;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 顺序 ID 生成器，适合需要多个可预测 ID 的测试。
 */
public final class SequentialIdGenerator implements IdGenerator {

    private final String prefix;
    private final AtomicLong sequence = new AtomicLong();

    public SequentialIdGenerator(String prefix) { this.prefix = prefix == null ? "" : prefix; }

    @Override public String nextId() { return prefix + sequence.incrementAndGet(); }
}
