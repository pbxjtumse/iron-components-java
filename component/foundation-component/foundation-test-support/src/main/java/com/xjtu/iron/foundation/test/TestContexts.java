package com.xjtu.iron.foundation.test;

import com.xjtu.iron.foundation.context.ExecutionContext;
import com.xjtu.iron.foundation.context.StandardContextKeys;

/**
 * 测试执行上下文工厂。
 */
public final class TestContexts {

    private TestContexts() {}

    public static ExecutionContext simple(String requestId) {
        return ExecutionContext.builder().put(StandardContextKeys.REQUEST_ID, requestId).build();
    }
}
