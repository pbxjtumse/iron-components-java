package com.xjtu.iron.reliable.task.core.exception;

/** 相同 taskId 已经绑定到不同任务内容。 */
public final class ReliableTaskConflictException extends RuntimeException {
    public ReliableTaskConflictException(String message) {
        super(message);
    }
}
