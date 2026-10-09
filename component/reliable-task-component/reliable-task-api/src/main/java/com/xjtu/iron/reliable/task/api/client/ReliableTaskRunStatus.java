package com.xjtu.iron.reliable.task.api.client;

/** 一次显式执行请求的结果。 */
public enum ReliableTaskRunStatus {
    EXECUTED,
    NOT_FOUND,
    NOT_CLAIMED,
    STALE_COMPLETION
}
