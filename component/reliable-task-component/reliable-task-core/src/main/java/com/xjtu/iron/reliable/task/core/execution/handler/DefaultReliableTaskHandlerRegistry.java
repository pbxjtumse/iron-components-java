package com.xjtu.iron.reliable.task.core.execution.handler;

import com.xjtu.iron.reliable.task.api.execution.ReliableTaskHandler;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** 启动时校验 taskType 唯一性的不可变注册表。 */
public final class DefaultReliableTaskHandlerRegistry implements ReliableTaskHandlerRegistry {

    private final Map<String, ReliableTaskHandler> handlers;

    public DefaultReliableTaskHandlerRegistry(Collection<? extends ReliableTaskHandler> handlers) {
        Map<String, ReliableTaskHandler> values = new LinkedHashMap<>();
        if (handlers != null) {
            for (ReliableTaskHandler handler : handlers) {
                if (handler == null) {
                    continue;
                }
                String taskType = requireText(handler.taskType());
                ReliableTaskHandler previous = values.putIfAbsent(taskType, handler);
                if (previous != null) {
                    throw new IllegalArgumentException("duplicate ReliableTaskHandler taskType: " + taskType);
                }
            }
        }
        this.handlers = Map.copyOf(values);
    }

    @Override
    public Optional<ReliableTaskHandler> find(String taskType) {
        return Optional.ofNullable(handlers.get(requireText(taskType)));
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("taskType must not be blank");
        }
        return value.trim();
    }
}
