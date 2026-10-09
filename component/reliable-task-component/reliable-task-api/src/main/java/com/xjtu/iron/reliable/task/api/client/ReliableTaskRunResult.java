package com.xjtu.iron.reliable.task.api.client;

import com.xjtu.iron.reliable.task.api.model.ReliableTask;

public record ReliableTaskRunResult(
        ReliableTaskRunStatus status,
        ReliableTask claimedTask,
        ReliableTask finalTask) {
}
