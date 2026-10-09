package com.xjtu.iron.reliable.task.api.operation.admin;

import com.xjtu.iron.reliable.task.api.model.ReliableTask;

public record ReliableTaskAdminResult(ReliableTaskAdminStatus status, ReliableTask task) {
}
