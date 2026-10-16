package com.xjtu.iron.retry.api.exception;

import com.xjtu.iron.retry.api.execution.RetryResult;

import java.util.Objects;

/** executeAndGet 在逻辑执行最终未成功时抛出的统一运行时异常。 */
public final class RetryExecutionException extends RuntimeException {

    /** 固定序列化版本。 */
    private static final long serialVersionUID = 1L;

    /** 保留完整统一结果，但不参与 Java 序列化。 */
    private final transient RetryResult<?> retryResult;

    public RetryExecutionException(RetryResult<?> retryResult) {
        super(buildMessage(requireResult(retryResult)), retryResult.getFailure());
        this.retryResult = retryResult;
    }

    public RetryResult<?> getRetryResult() {
        return retryResult;
    }

    /** 在调用父类构造器前校验结果非空。 */
    private static RetryResult<?> requireResult(RetryResult<?> result) {
        return Objects.requireNonNull(result, "retryResult must not be null");
    }

    /** 生成不包含业务请求体和敏感异常消息的稳定异常文本。 */
    private static String buildMessage(RetryResult<?> result) {
        return "Retry execution did not succeed: operation=" + result.getOperationName()
                + ", policy=" + result.getPolicyName()
                + ", status=" + result.getStatus()
                + ", attempts=" + result.getAttempts()
                + ", failureCode=" + result.getFailureCode();
    }
}
