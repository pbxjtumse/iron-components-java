package com.xjtu.iron.reliable.task.api.operation.run;

import com.xjtu.iron.reliable.task.api.model.ReliableTask;

public record ReliableTaskRunResult(
        ReliableTaskRunStatus status,
        ReliableTask claimedTask,
        ReliableTask finalTask) {
}
