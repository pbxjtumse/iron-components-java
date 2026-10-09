package com.xjtu.iron.reliable.task.api.scan;

import java.util.Objects;

/** 一次桶扫描的统计快照。 */
public final class ReliableTaskScanReport {

    /** Repository 返回的候选任务总数。 */
    private final int candidates;

    /** 成功获得执行权的任务数量。 */
    private final int claimed;

    /** Handler 执行并成功完成状态迁移的任务数量。 */
    private final int executed;

    /** 因 owner 或 version 失效而拒绝完成写入的任务数量。 */
    private final int staleCompletions;

    /** 未能获得执行权而跳过的候选任务数量。 */
    private final int skipped;

    public ReliableTaskScanReport(
            int candidates,
            int claimed,
            int executed,
            int staleCompletions,
            int skipped) {
        this.candidates = candidates;
        this.claimed = claimed;
        this.executed = executed;
        this.staleCompletions = staleCompletions;
        this.skipped = skipped;
    }

    public int getCandidates() {
        return candidates;
    }

    public int getClaimed() {
        return claimed;
    }

    public int getExecuted() {
        return executed;
    }

    public int getStaleCompletions() {
        return staleCompletions;
    }

    public int getSkipped() {
        return skipped;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ReliableTaskScanReport that)) {
            return false;
        }
        return candidates == that.candidates
                && claimed == that.claimed
                && executed == that.executed
                && staleCompletions == that.staleCompletions
                && skipped == that.skipped;
    }

    @Override
    public int hashCode() {
        return Objects.hash(candidates, claimed, executed, staleCompletions, skipped);
    }

    @Override
    public String toString() {
        return "ReliableTaskScanReport{" +
                "candidates=" + candidates +
                ", claimed=" + claimed +
                ", executed=" + executed +
                ", staleCompletions=" + staleCompletions +
                ", skipped=" + skipped +
                '}';
    }
}
