package com.xjtu.iron.reliable.task.core.execution.handler;

import com.xjtu.iron.reliable.task.api.execution.ReliableTaskHandler;

import java.util.Optional;

/** 按 taskType 定位唯一 Handler。 */
public interface ReliableTaskHandlerRegistry {
    Optional<ReliableTaskHandler> find(String taskType);
}
