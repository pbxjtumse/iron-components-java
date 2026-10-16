package com.xjtu.iron.reliable.task.api.repository.claim;

import com.xjtu.iron.reliable.task.api.model.ReliableTask;

import java.time.Instant;
import java.util.Objects;

/** 使用扫描快照的 version 对任务进行二次 CAS 抢占。 */
public final class ReliableTaskClaimCommand {

    /** 扫描或点查得到的候选任务快照。 */
    private final ReliableTask candidate;

    /** 本次尝试获取执行权的实例标识。 */
    private final String ownerId;

    /** 发起抢占时的当前时间。 */
    private final Instant now;

    /** 抢占成功后写入的 Lease 到期时间。 */
    private final Instant leaseUntil;

    public ReliableTaskClaimCommand(
            ReliableTask candidate,
            String ownerId,
            Instant now,
            Instant leaseUntil) {
        if (candidate == null) {
            throw new IllegalArgumentException("candidate must not be null");
        }
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
        if (now == null || leaseUntil == null || !leaseUntil.isAfter(now)) {
            throw new IllegalArgumentException("leaseUntil must be after now");
        }
        this.candidate = candidate;
        this.ownerId = ownerId.trim();
        this.now = now;
        this.leaseUntil = leaseUntil;
    }

    public ReliableTask getCandidate() {
        return candidate;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public Instant getNow() {
        return now;
    }

    public Instant getLeaseUntil() {
        return leaseUntil;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ReliableTaskClaimCommand that)) {
            return false;
        }
        return candidate.equals(that.candidate)
                && ownerId.equals(that.ownerId)
                && now.equals(that.now)
                && leaseUntil.equals(that.leaseUntil);
    }

    @Override
    public int hashCode() {
        return Objects.hash(candidate, ownerId, now, leaseUntil);
    }

    @Override
    public String toString() {
        return "ReliableTaskClaimCommand{" +
                "candidate=" + candidate +
                ", ownerId='" + ownerId + '\'' +
                ", now=" + now +
                ", leaseUntil=" + leaseUntil +
                '}';
    }
}
