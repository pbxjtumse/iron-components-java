package com.xjtu.iron.retry.api.policy;

import java.util.function.Predicate;

/** 保存一条返回结果匹配规则。 */
final class RetryResultRule {

    private final Predicate<Object> predicate;
    private final RetryFailureCategory category;
    private final String failureCode;

    RetryResultRule(
            Predicate<Object> predicate,
            RetryFailureCategory category,
            String failureCode) {
        this.predicate = predicate;
        this.category = category;
        this.failureCode = failureCode;
    }

    Predicate<Object> predicate() {
        return predicate;
    }

    RetryFailureCategory category() {
        return category;
    }

    String failureCode() {
        return failureCode;
    }
}
