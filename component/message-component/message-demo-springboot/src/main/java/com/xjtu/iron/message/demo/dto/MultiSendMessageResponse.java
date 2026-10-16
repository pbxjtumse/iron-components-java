package com.xjtu.iron.message.demo.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 三个 Provider 并行发送的响应摘要。
 */
public final class MultiSendMessageResponse {

    /** 本次并行发送批次 ID，用于关联三个 Provider 的消息。 */
    private final String batchId;

    /** 发送开始时间。 */
    private final Instant startedAt;

    /** 发送结束时间。 */
    private final Instant completedAt;

    /** 本次目标 Provider 数量。 */
    private final int total;

    /** 明确确认成功的 Provider 数量。 */
    private final int confirmed;

    /** 非 CONFIRMED 的 Provider 数量。 */
    private final int failed;

    /** 各 Provider 明细。 */
    private final List<SendMessageResponse> results;

    public MultiSendMessageResponse(
            String batchId,
            Instant startedAt,
            Instant completedAt,
            List<SendMessageResponse> results) {
        this.batchId = batchId;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.results = results == null ? List.of() : new ArrayList<>(results);
        this.total = this.results.size();
        this.confirmed = (int) this.results.stream()
                .filter(result -> "CONFIRMED".equals(result.getStatus()))
                .count();
        this.failed = this.total - this.confirmed;
    }

    public String getBatchId() {
        return batchId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public int getTotal() {
        return total;
    }

    public int getConfirmed() {
        return confirmed;
    }

    public int getFailed() {
        return failed;
    }

    public List<SendMessageResponse> getResults() {
        return new ArrayList<>(results);
    }
}
