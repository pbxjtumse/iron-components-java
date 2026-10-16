package com.xjtu.iron.reliable.task.provider.testkit;

import com.xjtu.iron.reliable.task.api.model.ReliableTask;
import com.xjtu.iron.reliable.task.api.model.ReliableTaskKey;
import com.xjtu.iron.reliable.task.api.repository.ReliableTaskRepository;
import com.xjtu.iron.reliable.task.api.repository.claim.ReliableTaskClaimCommand;
import com.xjtu.iron.reliable.task.api.repository.claim.ReliableTaskClaimResult;
import com.xjtu.iron.reliable.task.api.repository.create.ReliableTaskCreateResult;
import com.xjtu.iron.reliable.task.api.repository.lease.ReliableTaskLeaseRenewCommand;
import com.xjtu.iron.reliable.task.api.repository.scan.ReliableTaskScanQuery;
import com.xjtu.iron.reliable.task.api.repository.transition.ReliableTaskAdminTransitionCommand;
import com.xjtu.iron.reliable.task.api.repository.transition.ReliableTaskTransitionCommand;
import com.xjtu.iron.reliable.task.api.state.ReliableTaskStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 所有 ReliableTaskRepository Provider 必须通过的持久化语义契约。
 *
 * <p>具体 Provider 测试只负责创建独立数据库和 Repository，本类统一验证创建幂等、扫描、
 * CAS 抢占、Lease、旧 owner 隔离及人工状态迁移，避免不同 Provider 演化出不同语义。</p>
 */
public abstract class ReliableTaskRepositoryContract {

    /** 返回当前测试方法独占的 Repository。 */
    protected abstract ReliableTaskRepository repository();

    @Test
    protected void shouldCreateFindAndTreatDuplicateTaskIdAsIdempotent() {
        ReliableTask task = task("task-create", Instant.parse("2026-10-09T00:00:00Z"), 3);

        assertThat(repository().create(task)).isEqualTo(ReliableTaskCreateResult.CREATED);
        assertThat(repository().create(task)).isEqualTo(ReliableTaskCreateResult.ALREADY_EXISTS);

        ReliableTask stored = repository().find(task.getKey()).orElseThrow();
        assertThat(stored.getTaskId()).isEqualTo(task.getTaskId());
        assertThat(stored.getTaskType()).isEqualTo(task.getTaskType());
        assertThat(stored.getStatus()).isEqualTo(ReliableTaskStatus.READY);
    }

    @Test
    protected void shouldReturnOnlyDueTasksFromRequestedStoreAndBucket() {
        Instant now = Instant.parse("2026-10-09T00:00:00Z");
        repository().create(task("task-due", now, 3));
        repository().create(task("task-future", now.plusSeconds(60), 3));

        List<ReliableTask> candidates = repository().findDue(
                new ReliableTaskScanQuery("default", 7, now, 10)
        );

        assertThat(candidates)
                .extracting(ReliableTask::getTaskId)
                .containsExactly("task-due");
    }

    @Test
    protected void shouldAllowOnlyOneOwnerAndRejectStaleOwnerCompletionAfterLeaseTakeover() {
        Instant now = Instant.parse("2026-10-09T00:00:00Z");
        ReliableTask task = task("task-claim", now, 3);
        repository().create(task);

        ReliableTask candidate = repository().findDue(
                new ReliableTaskScanQuery("default", 7, now, 10)
        ).get(0);
        ReliableTaskClaimResult ownerA = repository().tryClaim(new ReliableTaskClaimCommand(
                candidate, "owner-a", now, now.plusSeconds(10)
        ));
        ReliableTaskClaimResult duplicateClaim = repository().tryClaim(new ReliableTaskClaimCommand(
                candidate, "owner-b", now, now.plusSeconds(10)
        ));

        assertThat(ownerA.isClaimed()).isTrue();
        assertThat(duplicateClaim.isClaimed()).isFalse();

        Instant takeoverTime = now.plusSeconds(11);
        ReliableTask expired = repository().findDue(
                new ReliableTaskScanQuery("default", 7, takeoverTime, 10)
        ).get(0);
        ReliableTaskClaimResult ownerB = repository().tryClaim(new ReliableTaskClaimCommand(
                expired, "owner-b", takeoverTime, takeoverTime.plusSeconds(10)
        ));

        assertThat(ownerB.isClaimed()).isTrue();
        assertThat(ownerB.getTask().getVersion()).isEqualTo(ownerA.getTask().getVersion() + 1);

        boolean staleWrite = repository().transition(new ReliableTaskTransitionCommand(
                ownerA.getTask().getKey(),
                ownerA.getTask().getOwnerId(),
                ownerA.getTask().getVersion(),
                ReliableTaskStatus.SUCCEEDED,
                null,
                null,
                null,
                takeoverTime
        ));
        boolean currentWrite = repository().transition(new ReliableTaskTransitionCommand(
                ownerB.getTask().getKey(),
                ownerB.getTask().getOwnerId(),
                ownerB.getTask().getVersion(),
                ReliableTaskStatus.SUCCEEDED,
                null,
                null,
                null,
                takeoverTime.plusSeconds(1)
        ));

        assertThat(staleWrite).isFalse();
        assertThat(currentWrite).isTrue();
        assertThat(repository().find(task.getKey()).orElseThrow().getStatus())
                .isEqualTo(ReliableTaskStatus.SUCCEEDED);
    }

    @Test
    protected void shouldRenewOnlyCurrentUnexpiredLease() {
        Instant now = Instant.parse("2026-10-09T00:00:00Z");
        ReliableTask task = task("task-renew", now, 3);
        repository().create(task);
        ReliableTaskClaimResult claim = repository().tryClaim(new ReliableTaskClaimCommand(
                task, "owner-a", now, now.plusSeconds(10)
        ));

        assertThat(repository().renewLease(new ReliableTaskLeaseRenewCommand(
                task.getKey(),
                "owner-a",
                claim.getTask().getVersion(),
                now.plusSeconds(5),
                now.plusSeconds(30)
        ))).isTrue();
        assertThat(repository().renewLease(new ReliableTaskLeaseRenewCommand(
                task.getKey(),
                "wrong-owner",
                claim.getTask().getVersion(),
                now.plusSeconds(6),
                now.plusSeconds(40)
        ))).isFalse();
    }

    @Test
    protected void shouldProtectAdministrativeCancellationWithStatusAndVersion() {
        Instant now = Instant.parse("2026-10-09T00:00:00Z");
        ReliableTask task = task("task-admin", now, 3);
        repository().create(task);

        assertThat(repository().adminTransition(new ReliableTaskAdminTransitionCommand(
                task.getKey(),
                ReliableTaskStatus.READY,
                99,
                ReliableTaskStatus.CANCELLED,
                null,
                false,
                now
        ))).isFalse();
        assertThat(repository().adminTransition(new ReliableTaskAdminTransitionCommand(
                task.getKey(),
                ReliableTaskStatus.READY,
                0,
                ReliableTaskStatus.CANCELLED,
                null,
                false,
                now
        ))).isTrue();
        assertThat(repository().find(task.getKey()).orElseThrow().getStatus())
                .isEqualTo(ReliableTaskStatus.CANCELLED);
    }

    private static ReliableTask task(String taskId, Instant executeAt, int maxAttempts) {
        return new ReliableTask(
                ReliableTaskKey.defaults(taskId),
                "demo",
                "business-1",
                "{}",
                "order-1",
                7,
                ReliableTaskStatus.READY,
                0,
                maxAttempts,
                executeAt,
                null,
                null,
                0,
                null,
                null,
                executeAt,
                executeAt,
                null
        );
    }
}
