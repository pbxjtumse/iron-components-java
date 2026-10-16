package com.xjtu.iron.concurrency.core.execution;

import com.xjtu.iron.concurrency.api.enums.QueueType;
import com.xjtu.iron.concurrency.api.enums.RejectionPolicy;
import com.xjtu.iron.concurrency.api.exception.ThreadPoolNotFoundException;
import com.xjtu.iron.concurrency.api.execution.pool.ThreadPoolSpec;
import com.xjtu.iron.concurrency.core.spi.RejectedExecutionHandlerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.*;

class ThreadPoolInfrastructureTest {

    private ThreadPoolExecutor executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void threadPoolSpecValidationCoversQueueAndTimingBranches() {
        ThreadPoolSpec valid = validSpec();
        assertDoesNotThrow(valid::validate);
        assertNotNull(valid.getThreadNamePrefix());

        ThreadPoolSpec direct = validSpec();
        direct.setQueueType(QueueType.DIRECT_HANDOFF);
        direct.setQueueCapacity(0);
        assertDoesNotThrow(direct::validate);

        ThreadPoolSpec invalidCapacity = validSpec();
        invalidCapacity.setQueueCapacity(0);
        assertThrows(IllegalArgumentException.class, invalidCapacity::validate);

        ThreadPoolSpec invalidKeepAlive = validSpec();
        invalidKeepAlive.setKeepAliveTime(Duration.ofMillis(-1));
        assertThrows(IllegalArgumentException.class, invalidKeepAlive::validate);

        ThreadPoolSpec invalidCoreTimeout = validSpec();
        invalidCoreTimeout.setAllowCoreThreadTimeout(true);
        invalidCoreTimeout.setKeepAliveTime(Duration.ZERO);
        assertThrows(IllegalArgumentException.class, invalidCoreTimeout::validate);

        ThreadPoolSpec invalidBlockingWait = validSpec();
        invalidBlockingWait.setRejectionPolicy(RejectionPolicy.BLOCKING_WAIT);
        invalidBlockingWait.setRejectionWaitTime(Duration.ZERO);
        assertThrows(IllegalArgumentException.class, invalidBlockingWait::validate);

        ThreadPoolSpec invalidAwait = validSpec();
        invalidAwait.setWaitForTasksToCompleteOnShutdown(true);
        invalidAwait.setAwaitTermination(Duration.ZERO);
        assertThrows(IllegalArgumentException.class, invalidAwait::validate);
    }

    @Test
    void registryRejectsDuplicateNameAndReturnsSnapshotCopy() {
        DefaultThreadPoolRegistry registry = new DefaultThreadPoolRegistry();
        executor = new ThreadPoolExecutor(
                1,
                1,
                60L,
                java.util.concurrent.TimeUnit.SECONDS,
                new java.util.concurrent.ArrayBlockingQueue<>(1)
        );
        ThreadPoolExecutor other = new ThreadPoolExecutor(
                1,
                1,
                60L,
                java.util.concurrent.TimeUnit.SECONDS,
                new java.util.concurrent.ArrayBlockingQueue<>(1)
        );

        try {
            registry.register("pool", executor);
            assertSame(executor, registry.getExecutor("pool"));
            assertThrows(IllegalStateException.class, () -> registry.register("pool", other));

            Map<String, ThreadPoolExecutor> snapshot = registry.snapshot();
            assertThrows(UnsupportedOperationException.class, () -> snapshot.put("x", other));
            assertThrows(ThreadPoolNotFoundException.class, () -> registry.getExecutor("missing"));
        } finally {
            other.shutdownNow();
        }
    }

    @Test
    void factoryCreatesQueueTypesAndRejectedHandlers() {
        RejectedExecutionHandlerFactory handlerFactory = new DefaultRejectedExecutionHandlerFactory();
        DefaultThreadPoolFactory factory = new DefaultThreadPoolFactory(handlerFactory);

        executor = factory.create(validSpec());
        assertEquals(1, executor.getCorePoolSize());
        assertEquals(2, executor.getMaximumPoolSize());

        executor.shutdownNow();
        ThreadPoolSpec direct = validSpec();
        direct.setQueueType(QueueType.DIRECT_HANDOFF);
        direct.setQueueCapacity(0);
        executor = factory.create(direct);
        assertEquals(0, executor.getQueue().remainingCapacity());
    }

    private ThreadPoolSpec validSpec() {
        ThreadPoolSpec spec = new ThreadPoolSpec();
        spec.setName("pool");
        spec.setCorePoolSize(1);
        spec.setMaxPoolSize(2);
        spec.setQueueCapacity(2);
        spec.setKeepAliveTime(Duration.ofMillis(100));
        spec.setAwaitTermination(Duration.ofMillis(100));
        spec.setRejectionWaitTime(Duration.ofMillis(10));
        return spec;
    }
}
