package com.xjtu.iron.concurrency.core.control;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 基于内存的当前节点运行任务控制注册表。
 */
public final class DefaultTaskControlRegistry
        implements TaskControlRegistry {

    /**
     * taskId 到运行控制对象的映射。
     */
    private final ConcurrentMap<String, CancellableTask> controls = new ConcurrentHashMap<>();

    @Override
    public void register(String taskId, CancellableTask task) {
        if (taskId == null || taskId.isBlank() || task == null) {
            throw new IllegalArgumentException("taskId and task must not be null");
        }

        CancellableTask previous = controls.putIfAbsent(taskId, task);
        if (previous != null) {
            throw new IllegalStateException("Running taskId already exists: " + taskId);
        }
    }

    @Override
    public Optional<CancellableTask> get(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(controls.get(taskId));
    }

    @Override
    public void remove(String taskId, CancellableTask task) {
        if (taskId == null || task == null) {
            return;
        }
        controls.remove(taskId, task);
    }
}
