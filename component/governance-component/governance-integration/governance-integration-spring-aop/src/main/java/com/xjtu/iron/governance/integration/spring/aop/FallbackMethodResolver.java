package com.xjtu.iron.governance.integration.spring.aop;

import java.lang.reflect.Method;

public class FallbackMethodResolver {

    public Method resolve(Class<?> targetClass,
                          String fallbackMethodName,
                          Object[] args) {
        Method[] methods = targetClass.getDeclaredMethods();

        for (Method method : methods) {
            if (!method.getName().equals(fallbackMethodName)) {
                continue;
            }

            if (method.getParameterCount() == args.length) {
                return method;
            }

            if (method.getParameterCount() == args.length + 1
                    && Throwable.class.isAssignableFrom(method.getParameterTypes()[args.length])) {
                return method;
            }
        }

        throw new IllegalStateException("Fallback method not found: " + fallbackMethodName);
    }
}