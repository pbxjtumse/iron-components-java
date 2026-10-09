package com.xjtu.iron.reliable.task.api.scan;

/** 一次桶扫描的统计快照。 */
public record ReliableTaskScanReport(
        int candidates,
        int claimed,
        int executed,
        int staleCompletions,
        int skipped) {
}
