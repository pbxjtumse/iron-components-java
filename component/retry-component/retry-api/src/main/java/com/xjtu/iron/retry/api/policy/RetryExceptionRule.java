package com.xjtu.iron.retry.api.policy;

/** 保存一条异常类型匹配规则。 */
final class RetryExceptionRule {

    private final Class<? extends Throwable> exceptionType;
    private final RetryFailureCategory category;
    private final String failureCode;

    RetryExceptionRule(
            Class<? extends Throwable> exceptionType,
            RetryFailureCategory category,
            String failureCode) {
        this.exceptionType = exceptionType;
        this.category = category;
        this.failureCode = failureCode;
    }

    Class<? extends Throwable> exceptionType() {
        return exceptionType;
    }

    RetryFailureCategory category() {
        return category;
    }

    String failureCode() {
        return failureCode;
    }
}
