package com.xjtu.iron.reliable.task.api.repository.lease;

import com.xjtu.iron.reliable.task.api.model.ReliableTaskKey;

import java.time.Instant;
import java.util.Objects;

/** 当前 owner 的 Lease 条件续租请求。 */
public final class ReliableTaskLeaseRenewCommand {

    /** 需要续租的任务主键。 */
    private final ReliableTaskKey key;

    /** 当前持有 Lease 的实例标识。 */
    private final String ownerId;

    /** 续租时必须匹配的任务版本。 */
    private final long expectedVersion;

    /** 发起续租时的当前时间。 */
    private final Instant now;

    /** 本次续租期望写入的新到期时间。 */
    private final Instant leaseUntil;

    public ReliableTaskLeaseRenewCommand(
            ReliableTaskKey key,
            String ownerId,
            long expectedVersion,
            Instant now,
            Instant leaseUntil) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
        if (expectedVersion < 1) {
            throw new IllegalArgumentException("expectedVersion must be positive");
        }
        if (now == null || leaseUntil == null || !leaseUntil.isAfter(now)) {
            throw new IllegalArgumentException("leaseUntil must be after now");
        }
        this.key = key;
        this.ownerId = ownerId.trim();
        this.expectedVersion = expectedVersion;
        this.now = now;
        this.leaseUntil = leaseUntil;
    }

    public ReliableTaskKey getKey() {
        return key;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public long getExpectedVersion() {
        return expectedVersion;
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
        if (!(object instanceof ReliableTaskLeaseRenewCommand that)) {
            return false;
        }
        return expectedVersion == that.expectedVersion
                && key.equals(that.key)
                && ownerId.equals(that.ownerId)
                && now.equals(that.now)
                && leaseUntil.equals(that.leaseUntil);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, ownerId, expectedVersion, now, leaseUntil);
    }

    @Override
    public String toString() {
        return "ReliableTaskLeaseRenewCommand{" +
                "key=" + key +
                ", ownerId='" + ownerId + '\'' +
                ", expectedVersion=" + expectedVersion +
                ", now=" + now +
                ", leaseUntil=" + leaseUntil +
                '}';
    }
}
