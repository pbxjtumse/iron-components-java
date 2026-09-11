package com.xjtu.iron.retry.config.properties;

import com.xjtu.iron.retry.api.backoff.BackoffStrategies;
import com.xjtu.iron.retry.api.backoff.BackoffStrategy;
import com.xjtu.iron.retry.api.policy.RetryPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 将完成继承解析的 Spring 配置转换为核心 RetryPolicy。 */
final class RetryPolicyFactory {

    /** 根据命名和解析配置创建不可变策略。 */
    RetryPolicy create(String policyName, ResolvedRetryPolicyProperties properties) {
        ResolvedRetryPolicyProperties actualProperties = Objects.requireNonNull(
                properties,
                "properties must not be null"
        );
        RetryPolicy.Builder builder = RetryPolicy.builder(policyName)
                .maxAttempts(actualProperties.maxAttempts)
                .maxDuration(actualProperties.maxDuration)
                .operationSafety(actualProperties.operationSafety)
                .safetyMode(actualProperties.safetyMode)
                .traverseCauses(actualProperties.traverseCauses)
                .maxCauseDepth(actualProperties.maxCauseDepth)
                .backoffStrategy(createBackoff(actualProperties));

        List<Class<? extends Throwable>> retryOn = loadThrowableTypes(
                actualProperties.retryOn
        );
        if (!retryOn.isEmpty()) {
            builder.retryOn(
                    actualProperties.retryFailureCategory,
                    actualProperties.retryFailureCode,
                    toThrowableArray(retryOn)
            );
        }

        List<Class<? extends Throwable>> stopOn = loadThrowableTypes(
                actualProperties.stopOn
        );
        if (!stopOn.isEmpty()) {
            builder.stopOn(toThrowableArray(stopOn));
        }

        List<Class<? extends Throwable>> abortOn = loadThrowableTypes(
                actualProperties.abortOn
        );
        if (!abortOn.isEmpty()) {
            builder.abortOn(toThrowableArray(abortOn));
        }

        return builder.build();
    }

    /** 根据解析后的枚举类型创建退避策略。 */
    private BackoffStrategy createBackoff(ResolvedRetryPolicyProperties properties) {
        return switch (properties.backoffType) {
            case NONE -> BackoffStrategies.none();
            case FIXED -> BackoffStrategies.fixed(properties.delay);
            case EXPONENTIAL -> BackoffStrategies.exponential(
                    properties.initialDelay,
                    properties.maxDelay,
                    properties.multiplier
            );
            case EXPONENTIAL_FULL_JITTER ->
                    BackoffStrategies.exponentialWithFullJitter(
                            properties.initialDelay,
                            properties.maxDelay,
                            properties.multiplier
                    );
        };
    }

    /** 将异常类型列表转换为构建器需要的泛型数组。 */
    @SuppressWarnings("unchecked")
    private Class<? extends Throwable>[] toThrowableArray(
            List<Class<? extends Throwable>> throwableTypes) {
        Class<? extends Throwable>[] target =
                (Class<? extends Throwable>[]) new Class<?>[throwableTypes.size()];
        return throwableTypes.toArray(target);
    }

    /** 使用线程上下文类加载器加载并校验异常类型。 */
    @SuppressWarnings("unchecked")
    private List<Class<? extends Throwable>> loadThrowableTypes(List<String> classNames) {
        List<Class<? extends Throwable>> result = new ArrayList<>();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader actualClassLoader = classLoader == null
                ? RetryPolicyFactory.class.getClassLoader()
                : classLoader;
        for (String className : classNames) {
            String actualClassName = requireText(className, "exception class name");
            try {
                Class<?> candidate = Class.forName(
                        actualClassName,
                        false,
                        actualClassLoader
                );
                if (!Throwable.class.isAssignableFrom(candidate)) {
                    throw new IllegalArgumentException(
                            actualClassName + " is not a Throwable type"
                    );
                }
                result.add((Class<? extends Throwable>) candidate);
            } catch (ClassNotFoundException exception) {
                throw new IllegalArgumentException(
                        "Retry exception class does not exist: " + actualClassName,
                        exception
                );
            }
        }
        return result;
    }

    /** 校验类名文本非空且非空白。 */
    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
