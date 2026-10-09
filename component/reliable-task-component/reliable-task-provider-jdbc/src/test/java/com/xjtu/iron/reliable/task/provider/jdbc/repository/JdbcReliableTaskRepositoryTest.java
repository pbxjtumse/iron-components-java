package com.xjtu.iron.reliable.task.provider.jdbc.repository;

import com.xjtu.iron.relational.api.RelationalTemplate;
import com.xjtu.iron.relational.core.DefaultRelationalTemplate;
import com.xjtu.iron.relational.core.connection.DefaultConnectionProvider;
import com.xjtu.iron.relational.core.connection.SingleDataSourceResolver;
import com.xjtu.iron.relational.core.exception.StandardSqlExceptionTranslator;
import com.xjtu.iron.reliable.task.api.model.ReliableTask;
import com.xjtu.iron.reliable.task.api.model.ReliableTaskKey;
import com.xjtu.iron.reliable.task.api.repository.claim.ReliableTaskClaimCommand;
import com.xjtu.iron.reliable.task.api.repository.claim.ReliableTaskClaimResult;
import com.xjtu.iron.reliable.task.api.repository.create.ReliableTaskCreateResult;
import com.xjtu.iron.reliable.task.api.repository.lease.ReliableTaskLeaseRenewCommand;
import com.xjtu.iron.reliable.task.api.repository.scan.ReliableTaskScanQuery;
import com.xjtu.iron.reliable.task.api.repository.transition.ReliableTaskAdminTransitionCommand;
import com.xjtu.iron.reliable.task.api.repository.transition.ReliableTaskTransitionCommand;
import com.xjtu.iron.reliable.task.api.state.ReliableTaskStatus;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcReliableTaskRepositoryTest {

    /** 每个测试用例使用的 JDBC 可靠任务仓储。 */
    private JdbcReliableTaskRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:reliable_task_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        createSchema(dataSource);
        RelationalTemplate relational = new DefaultRelationalTemplate(
                new DefaultConnectionProvider(new SingleDataSourceResolver(dataSource)),
                new StandardSqlExceptionTranslator()
        );
        repository = new JdbcReliableTaskRepository(relational);
    }

    @Test
    void shouldCreateFindAndTreatDuplicateTaskIdAsIdempotent() {
        ReliableTask task = task("task-1", Instant.parse("2026-10-09T00:00:00Z"), 3);

        assertThat(repository.create(task)).isEqualTo(ReliableTaskCreateResult.CREATED);
        assertThat(repository.create(task)).isEqualTo(ReliableTaskCreateResult.ALREADY_EXISTS);
        ReliableTask stored = repository.find(task.getKey()).orElseThrow();
        assertThat(stored.getTaskId()).isEqualTo(task.getTaskId());
        assertThat(stored.getTaskType()).isEqualTo(task.getTaskType());
        assertThat(stored.getStatus()).isEqualTo(ReliableTaskStatus.READY);
    }

    @Test
    void shouldAllowOnlyOneOwnerAndRejectStaleOwnerCompletionAfterLeaseTakeover() {
        Instant now = Instant.parse("2026-10-09T00:00:00Z");
        ReliableTask task = task("task-2", now, 3);
        repository.create(task);

        List<ReliableTask> candidates = repository.findDue(new ReliableTaskScanQuery("default", 7, now, 10));
        ReliableTaskClaimResult ownerA = repository.tryClaim(new ReliableTaskClaimCommand(
                candidates.get(0), "owner-a", now, now.plusSeconds(10)));
        ReliableTaskClaimResult duplicateClaim = repository.tryClaim(new ReliableTaskClaimCommand(
                candidates.get(0), "owner-b", now, now.plusSeconds(10)));

        assertThat(ownerA.isClaimed()).isTrue();
        assertThat(duplicateClaim.isClaimed()).isFalse();

        Instant takeoverTime = now.plusSeconds(11);
        ReliableTask expired = repository.findDue(new ReliableTaskScanQuery("default", 7, takeoverTime, 10)).get(0);
        ReliableTaskClaimResult ownerB = repository.tryClaim(new ReliableTaskClaimCommand(
                expired, "owner-b", takeoverTime, takeoverTime.plusSeconds(10)));

        assertThat(ownerB.isClaimed()).isTrue();
        assertThat(ownerB.getTask().getVersion()).isEqualTo(ownerA.getTask().getVersion() + 1);

        boolean staleWrite = repository.transition(new ReliableTaskTransitionCommand(
                ownerA.getTask().getKey(),
                ownerA.getTask().getOwnerId(),
                ownerA.getTask().getVersion(),
                ReliableTaskStatus.SUCCEEDED,
                null,
                null,
                null,
                takeoverTime
        ));
        boolean currentWrite = repository.transition(new ReliableTaskTransitionCommand(
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
        assertThat(repository.find(task.getKey()).orElseThrow().getStatus())
                .isEqualTo(ReliableTaskStatus.SUCCEEDED);
    }

    @Test
    void shouldRenewOnlyCurrentUnexpiredLease() {
        Instant now = Instant.parse("2026-10-09T00:00:00Z");
        ReliableTask task = task("task-3", now, 3);
        repository.create(task);
        ReliableTaskClaimResult claim = repository.tryClaim(new ReliableTaskClaimCommand(
                task, "owner-a", now, now.plusSeconds(10)));

        assertThat(repository.renewLease(new ReliableTaskLeaseRenewCommand(
                task.getKey(), "owner-a", claim.getTask().getVersion(), now.plusSeconds(5), now.plusSeconds(30)
        ))).isTrue();
        assertThat(repository.renewLease(new ReliableTaskLeaseRenewCommand(
                task.getKey(), "wrong-owner", claim.getTask().getVersion(), now.plusSeconds(6), now.plusSeconds(40)
        ))).isFalse();
    }

    @Test
    void shouldProtectAdministrativeCancellationWithStatusAndVersion() {
        Instant now = Instant.parse("2026-10-09T00:00:00Z");
        ReliableTask task = task("task-4", now, 3);
        repository.create(task);

        assertThat(repository.adminTransition(new ReliableTaskAdminTransitionCommand(
                task.getKey(), ReliableTaskStatus.READY, 99, ReliableTaskStatus.CANCELLED,
                null, false, now
        ))).isFalse();
        assertThat(repository.adminTransition(new ReliableTaskAdminTransitionCommand(
                task.getKey(), ReliableTaskStatus.READY, 0, ReliableTaskStatus.CANCELLED,
                null, false, now
        ))).isTrue();
        assertThat(repository.find(task.getKey()).orElseThrow().getStatus())
                .isEqualTo(ReliableTaskStatus.CANCELLED);
    }

    private static ReliableTask task(String taskId, Instant now, int maxAttempts) {
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
                now,
                null,
                null,
                0,
                null,
                null,
                now,
                now,
                null
        );
    }

    private static void createSchema(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE iron_reliable_task (
                        store_name VARCHAR(64) NOT NULL,
                        namespace VARCHAR(64) NOT NULL,
                        task_id VARCHAR(128) NOT NULL,
                        task_type VARCHAR(64) NOT NULL,
                        business_key VARCHAR(128),
                        payload CLOB NOT NULL,
                        route_key VARCHAR(256),
                        scan_bucket INT NOT NULL,
                        status VARCHAR(32) NOT NULL,
                        attempt_count INT NOT NULL,
                        max_attempts INT NOT NULL,
                        next_execute_at TIMESTAMP(6),
                        owner_id VARCHAR(128),
                        lease_until TIMESTAMP(6),
                        version BIGINT NOT NULL,
                        last_error_code VARCHAR(64),
                        last_error_message VARCHAR(1024),
                        created_at TIMESTAMP(6) NOT NULL,
                        updated_at TIMESTAMP(6) NOT NULL,
                        completed_at TIMESTAMP(6),
                        PRIMARY KEY (store_name, namespace, task_id)
                    )
                    """);
        }
    }
}
