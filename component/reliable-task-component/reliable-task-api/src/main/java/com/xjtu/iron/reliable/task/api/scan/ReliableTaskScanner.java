package com.xjtu.iron.reliable.task.api.scan;

/** 可被本地调度、手工入口或未来 XXL-JOB 统一调用的扫描门面。 */
public interface ReliableTaskScanner {
    ReliableTaskScanReport scan(ReliableTaskScanRequest request);
}
