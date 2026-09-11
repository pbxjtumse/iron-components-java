package com.xjtu.iron.retry.api.execution;

import com.xjtu.iron.retry.api.policy.RetryPolicy;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

/** 提供进程内同步重试的统一执行入口。 */
public interface RetryExecutor {

    /** 执行一个已经完整构建的重试请求。 */
    <T> RetryResult<T> execute(RetryExecution<T> execution);

    /** 使用命名策略执行带属性的业务操作。 */
    <T> RetryResult<T> execute(
            String operationName,
            Map<String, Object> attributes,
            RetryOperation<T> operation,
            String policyName);

    /** 使用显式策略执行带属性的业务操作。 */
    default <T> RetryResult<T> execute(
            String operationName,
            Map<String, Object> attributes,
            RetryOperation<T> operation,
            RetryPolicy retryPolicy) {
        RetryExecution<T> execution = RetryExecution
                .builder(operationName, operation, retryPolicy)
                .attributes(attributes)
                .build();
        return execute(execution);
    }

    /** 使用显式策略执行不带属性的业务操作。 */
    default <T> RetryResult<T> execute(
            String operationName,
            RetryOperation<T> operation,
            RetryPolicy retryPolicy) {
        return execute(operationName, Collections.emptyMap(), operation, retryPolicy);
    }

    /** 使用命名策略执行不带属性的业务操作。 */
    default <T> RetryResult<T> execute(
            String operationName,
            RetryOperation<T> operation,
            String policyName) {
        return execute(operationName, Collections.emptyMap(), operation, policyName);
    }

    /** 使用默认匿名操作名执行业务操作。 */
    default <T> RetryResult<T> execute(
            RetryOperation<T> operation,
            RetryPolicy retryPolicy) {
        return execute("anonymous", Collections.emptyMap(), operation, retryPolicy);
    }

    /** 将 Callable 适配为 RetryOperation 后执行。 */
    default <T> RetryResult<T> execute(
            String operationName,
            Callable<T> callable,
            RetryPolicy retryPolicy) {
        Objects.requireNonNull(callable, "callable must not be null");
        return execute(operationName, context -> callable.call(), retryPolicy);
    }

    /** 将 Callable 适配为 RetryOperation 并使用命名策略执行。 */
    default <T> RetryResult<T> execute(
            String operationName,
            Callable<T> callable,
            String policyName) {
        Objects.requireNonNull(callable, "callable must not be null");
        return execute(operationName, context -> callable.call(), policyName);
    }

    /** 将 Runnable 适配为无返回值操作后执行。 */
    default RetryResult<Void> run(
            String operationName,
            Runnable runnable,
            RetryPolicy retryPolicy) {
        Objects.requireNonNull(runnable, "runnable must not be null");
        return execute(operationName, context -> {
            runnable.run();
            return null;
        }, retryPolicy);
    }

    /** 将 Runnable 适配为无返回值操作并使用命名策略执行。 */
    default RetryResult<Void> run(
            String operationName,
            Runnable runnable,
            String policyName) {
        Objects.requireNonNull(runnable, "runnable must not be null");
        return execute(operationName, context -> {
            runnable.run();
            return null;
        }, policyName);
    }

    /** 执行并在最终失败时抛出统一 RetryExecutionException。 */
    default <T> T executeAndGet(
            String operationName,
            RetryOperation<T> operation,
            RetryPolicy retryPolicy) {
        return execute(operationName, operation, retryPolicy).getOrThrow();
    }

    /** 使用命名策略执行并在最终失败时抛出统一异常。 */
    default <T> T executeAndGet(
            String operationName,
            RetryOperation<T> operation,
            String policyName) {
        return execute(operationName, operation, policyName).getOrThrow();
    }
}
