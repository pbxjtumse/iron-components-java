package com.xjtu.iron.concurrency.core.task;

import com.xjtu.iron.concurrency.api.execution.task.AsyncTask;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class TaskDefinitionSnapshotTest {

    @Test
    void fromCreatesStableSnapshotFromMutableAsyncTask() {
        Supplier<String> originalOperation = () -> "operation-A";
        Function<Throwable, String> originalFallback = error -> "fallback-A";

        AsyncTask<String> task = AsyncTask.of("pool-A", "task-A", originalOperation)
                .taskId("task-1")
                .bizKey("biz-A")
                .description("desc-A")
                .tag("scene", "A")
                .timeout(Duration.ofSeconds(1))
                .queueTimeout(Duration.ofMillis(100))
                .cancelOnTimeout(true)
                .interruptOnCancel(true)
                .fallback(originalFallback)
                .contextPropagation(false);

        task.validate();
        TaskDefinition<String> definition = TaskDefinition.from(task);

        task.setTaskId("task-2");
        task.setExecutorName("pool-B");
        task.setTaskName("task-B");
        task.setBizKey("biz-B");
        task.setTimeout(Duration.ofSeconds(2));
        task.setQueueTimeout(Duration.ofSeconds(3));
        task.setCancelOnTimeout(false);
        task.setInterruptOnTimeout(false);
        task.setOperation(() -> "operation-B");
        task.setFallback(error -> "fallback-B");
        task.setContextPropagation(true);
        task.setTags(Map.of("scene", "B"));

        assertEquals("task-1", definition.getTaskId());
        assertEquals("pool-A", definition.getExecutorName());
        assertEquals("task-A", definition.getTaskName());
        assertEquals("biz-A", definition.getBizKey());
        assertEquals("A", definition.getMetadata().getTags().get("scene"));
        assertEquals(Duration.ofSeconds(1), definition.getTimeout());
        assertEquals(Duration.ofMillis(100), definition.getQueueTimeout());
        assertTrue(definition.isCancelOnTimeout());
        assertTrue(definition.isInterruptOnTimeout());
        assertFalse(definition.isContextPropagation());
        assertSame(originalOperation, definition.getOperation());
        assertSame(originalFallback, definition.getFallback());
        assertEquals("operation-A", definition.getOperation().get());
        assertEquals("fallback-A", definition.getFallback().apply(new RuntimeException()));
    }

    @Test
    void fromRejectsNullTask() {
        assertThrows(NullPointerException.class, () -> TaskDefinition.from(null));
    }
}
