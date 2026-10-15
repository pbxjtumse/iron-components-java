package com.xjtu.iron;

import lombok.Getter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Getter
public enum ExecutorSpec {

    ORDER("order-executor", new HashSet<>(Arrays.asList("order", "api"))), PAY("pay-executor", new HashSet<>(Arrays.asList("pay")));

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


