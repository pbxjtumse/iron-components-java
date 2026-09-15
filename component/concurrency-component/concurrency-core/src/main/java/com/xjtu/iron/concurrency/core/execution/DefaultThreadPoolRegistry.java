package com.xjtu.iron.concurrency.core.execution;

import com.xjtu.iron.concurrency.api.exception.ThreadPoolNotFoundException;
import com.xjtu.iron.concurrency.core.spi.ThreadPoolRegistry;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 默认线程池注册中心。
 */
public class DefaultThreadPoolRegistry implements ThreadPoolRegistry {

    private final Map<String, ThreadPoolExecutor> executors = new ConcurrentHashMap<>();

    @Override
    public ThreadPoolExecutor getExecutor(String executorName) {
        ThreadPoolExecutor executor = executors.get(executorName);
        if (executor == null) {
            throw new ThreadPoolNotFoundException(executorName);
        }
        return executor;
    }

    @Override
    public void register(String executorName, ThreadPoolExecutor executor) {
        Objects.requireNonNull(executorName, "executorName must not be null");
        Objects.requireNonNull(executor, "executor must not be null");
        ThreadPoolExecutor existing = executors.putIfAbsent(executorName, executor);
        if (existing != null && existing != executor) {
            throw new IllegalStateException("Thread pool already registered: " + executorName);
        }
    }

    @Override
    public Map<String, ThreadPoolExecutor> snapshot() {
        //使用copy 返回真正的时点快照，而不是使用  return Map.copyOf(executors);
        return Map.copyOf(executors);
    }

    @Override
    public void replace(String name, ThreadPoolExecutor expectedOld, ThreadPoolExecutor newExecutor) {
//        CAS 替换 Registry
//        旧池停止接收任务
//        旧池优雅关闭
//         新池开始服务
    }
}
