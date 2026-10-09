package com.xjtu.iron.reliable.task.core.scan;

import com.xjtu.iron.reliable.task.api.operation.run.ReliableTaskRunResult;
import com.xjtu.iron.reliable.task.api.operation.run.ReliableTaskRunStatus;
import com.xjtu.iron.reliable.task.api.model.ReliableTask;
import com.xjtu.iron.reliable.task.api.repository.ReliableTaskRepository;
import com.xjtu.iron.reliable.task.api.repository.scan.ReliableTaskScanQuery;
import com.xjtu.iron.reliable.task.api.scan.ReliableTaskScanReport;
import com.xjtu.iron.reliable.task.api.scan.ReliableTaskScanRequest;
import com.xjtu.iron.reliable.task.api.scan.ReliableTaskScanner;
import com.xjtu.iron.reliable.task.core.execution.ReliableTaskEngine;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 单桶串行扫描实现；并发度由外层触发器决定。 */
public final class DefaultReliableTaskScanner implements ReliableTaskScanner {

    private final ReliableTaskRepository repository;
    private final ReliableTaskEngine engine;
    private final Clock clock;

    public DefaultReliableTaskScanner(
            ReliableTaskRepository repository,
            ReliableTaskEngine engine,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public ReliableTaskScanReport scan(ReliableTaskScanRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Instant now = clock.instant();
        List<ReliableTask> candidates = repository.findDue(new ReliableTaskScanQuery(
                request.storeName(), request.scanBucket(), now, request.batchSize()
        ));

        int claimed = 0;
        int executed = 0;
        int stale = 0;
        int skipped = 0;
        for (ReliableTask candidate : candidates) {
            ReliableTaskRunResult result = engine.runCandidate(candidate);
            switch (result.status()) {
                case EXECUTED -> {
                    claimed++;
                    executed++;
                }
                case STALE_COMPLETION -> {
                    claimed++;
                    stale++;
                }
                case NOT_CLAIMED, NOT_FOUND -> skipped++;
            }
        }
        return new ReliableTaskScanReport(candidates.size(), claimed, executed, stale, skipped);
    }
}
