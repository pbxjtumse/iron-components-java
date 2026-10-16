package com.xjtu.iron.concurrency.core.lifecycle;

import com.xjtu.iron.concurrency.api.enums.task.AsyncTaskStatus;
import com.xjtu.iron.concurrency.api.error.AsyncError;
import com.xjtu.iron.concurrency.api.event.TaskExecutionEvent;
import com.xjtu.iron.concurrency.api.execution.registry.TaskExecutionRegistry;
import com.xjtu.iron.concurrency.api.execution.registry.TaskExecutionSnapshot;
import com.xjtu.iron.concurrency.api.listener.TaskExecutionListener;
import com.xjtu.iron.concurrency.api.task.TaskExecutionMode;
import com.xjtu.iron.concurrency.api.task.TaskMetadata;
import com.xjtu.iron.concurrency.api.task.TaskResultMode;
import com.xjtu.iron.concurrency.api.task.TaskTimingSnapshot;
import com.xjtu.iron.concurrency.core.metrics.NoopConcurrencyMetricsRecorder;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultTaskLifecyclePublisherTest {

    @Test
    void publishCompletedDoesNotWriteRegistryAgain() {
        CountingRegistry registry = new CountingRegistry();
        RecordingListener listener = new RecordingListener();
        DefaultTaskLifecyclePublisher publisher = new DefaultTaskLifecyclePublisher(
                new NoopConcurrencyMetricsRecorder(),
                registry,
                listener
        );
        TaskExecutionEvent event = event(AsyncTaskStatus.SUCCESS);

        publisher.publish(event);
        publisher.publishCompleted(event);

        assertEquals(1, registry.updates.get());
        assertEquals(List.of(AsyncTaskStatus.SUCCESS), listener.completed);
    }

    @Test
    void publishRoutesSpecificListenerCallbacks() {
        CountingRegistry registry = new CountingRegistry();
        RecordingListener listener = new RecordingListener();
        DefaultTaskLifecyclePublisher publisher = new DefaultTaskLifecyclePublisher(
                new NoopConcurrencyMetricsRecorder(),
                registry,
                listener
        );

        publisher.publish(event(AsyncTaskStatus.SUBMITTED));
        publisher.publish(event(AsyncTaskStatus.RUNNING));
        publisher.publish(event(AsyncTaskStatus.REJECTED));
        publisher.publish(event(AsyncTaskStatus.FALLBACK));
        publisher.publish(event(AsyncTaskStatus.FALLBACK_FAILED));

        assertEquals(List.of(
                AsyncTaskStatus.SUBMITTED,
                AsyncTaskStatus.RUNNING,
                AsyncTaskStatus.REJECTED,
                AsyncTaskStatus.FALLBACK,
                AsyncTaskStatus.FALLBACK_FAILED
        ), listener.specific);
        assertEquals(5, registry.updates.get());
    }

    private TaskExecutionEvent event(AsyncTaskStatus status) {
        return new TaskExecutionEvent(
                new TaskMetadata("task-1", "pool", "task", null, null, null),
                status,
                TaskResultMode.RESULT_AWARE,
                TaskExecutionMode.THREAD_POOL,
                TaskTimingSnapshot.empty(),
                AsyncError.none(),
                status.name(),
                Instant.now()
        );
    }

    private static final class CountingRegistry implements TaskExecutionRegistry {
        private final AtomicInteger updates = new AtomicInteger();

        @Override
        public void update(TaskExecutionSnapshot snapshot) {
            updates.incrementAndGet();
        }

        @Override
        public Optional<TaskExecutionSnapshot> get(String taskId) {
            return Optional.empty();
        }

        @Override
        public List<TaskExecutionSnapshot> recent(int limit) {
            return List.of();
        }
    }

    private static final class RecordingListener implements TaskExecutionListener {
        private final List<AsyncTaskStatus> specific = new ArrayList<>();
        private final List<AsyncTaskStatus> completed = new ArrayList<>();

        @Override public void onSubmitted(TaskExecutionEvent event) { specific.add(event.getStatus()); }
        @Override public void onStarted(TaskExecutionEvent event) { specific.add(event.getStatus()); }
        @Override public void onRejected(TaskExecutionEvent event) { specific.add(event.getStatus()); }
        @Override public void onFallback(TaskExecutionEvent event) { specific.add(event.getStatus()); }
        @Override public void onFallbackFailure(TaskExecutionEvent event) { specific.add(event.getStatus()); }
        @Override public void onCompleted(TaskExecutionEvent event) { completed.add(event.getStatus()); }
    }
}
