package com.xjtu.iron;

import lombok.Getter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** 应用示例中的业务线程池规格。 */
@Getter
public enum ExecutorSpec {

    /** 处理订单与 API 标签任务的线程池。 */
    ORDER("order-executor", new HashSet<>(Arrays.asList("order", "api"))),

    /** 处理支付标签任务的线程池。 */
    PAY("pay-executor", new HashSet<>(Arrays.asList("pay")));

    private final String poolName;
    private final Set<String> tags;

    ExecutorSpec(String poolName, Set<String> tags) {
        this.poolName = poolName;
        this.tags = tags;
    }

    public String poolName() {
        return poolName;
    }

    public Set<String> tags() {
        return tags;
    }
}


