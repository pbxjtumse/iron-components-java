package com.xjtu.iron.reliable.task.api.operation.submit;

import com.xjtu.iron.reliable.task.api.model.ReliableTask;

public record ReliableTaskSubmitResult(ReliableTaskSubmitStatus status, ReliableTask task) {
}
