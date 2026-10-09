package com.xjtu.iron.reliable.task.api.client;

import com.xjtu.iron.reliable.task.api.model.ReliableTask;

public record ReliableTaskSubmitResult(ReliableTaskSubmitStatus status, ReliableTask task) {
}
