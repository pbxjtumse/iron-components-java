package com.xjtu.iron.concurrency.core.registry;

import com.xjtu.iron.concurrency.api.enums.task.AsyncTaskStatus;
import com.xjtu.iron.concurrency.api.error.AsyncError;
import com.xjtu.iron.concurrency.api.event.TaskExecutionEvent;
import com.xjtu.iron.concurrency.api.execution.registry.TaskExecutionSnapshot;
import com.xjtu.iron.concurrency.api.task.TaskExecutionMode;
import com.xjtu.iron.concurrency.api.task.TaskMetadata;
import com.xjtu.iron.concurrency.api.task.TaskResultMode;
import com.xjtu.iron.concurrency.api.task.TaskTimingSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultTaskExecutionRegistryTest {

    @Test
    void updateGetAndRecentReturnLatestVersionOnly() {
        DefaultTaskExecutionRegistry registry = new DefaultTaskExecutionRegistry(100);

        registry.update(snapshot("task-1", AsyncTaskStatus.SUBMITTED, 1));
        registry.update(snapshot("task-1", AsyncTaskStatus.RUNNING, 2));
        registry.update(snapshot("task-2", AsyncTaskStatus.SUCCESS, 3));

        assertEquals(AsyncTaskStatus.RUNNING, registry.get("task-1").orElseThrow().getStatus());

        List<TaskExecutionSnapshot> recent = registry.recent(10);
        assertEquals("task-2", recent.get(0).getTaskId());
        assertEquals("task-1", recent.get(1).getTaskId());
        assertEquals(2, recent.size());
    }

    @Test
    void evictsOldestDifferentTaskIdsWhenOverCapacity() {
        DefaultTaskExecutionRegistry registry = new DefaultTaskExecutionRegistry(100);

        for (int i = 0; i < 120; i++) {
            registry.update(snapshot("task-" + i, AsyncTaskStatus.SUCCESS, i));
        }

        assertTrue(registry.recent(200).size() <= 100);
        assertTrue(registry.get("task-119").isPresent());
        assertTrue(registry.get("task-0").isEmpty());
    }

    @Test
    void ignoresInvalidSnapshot() {
        DefaultTaskExecutionRegistry registry = new DefaultTaskExecutionRegistry(100);

        registry.update(null);
        registry.update(new TaskExecutionSnapshot());

        assertTrue(registry.recent(10).isEmpty());
        assertTrue(registry.get(null).isEmpty());
        assertTrue(registry.get(" ").isEmpty());
    }

    private TaskExecutionSnapshot snapshot(String taskId, AsyncTaskStatus status, long offset) {
        TaskExecutionEvent event = new TaskExecutionEvent(
                new TaskMetadata(taskId, "pool", "task", null, null, null),
                status,
                TaskResultMode.RESULT_AWARE,
                TaskExecutionMode.THREAD_POOL,
                TaskTimingSnapshot.empty(),
                AsyncError.none(),
                status.name(),
                Instant.ofEpochMilli(1000 + offset)
        );
        return TaskExecutionSnapshot.from(event);
    }
}
