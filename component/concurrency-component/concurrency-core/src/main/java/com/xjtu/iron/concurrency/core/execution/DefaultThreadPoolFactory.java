package com.xjtu.iron.concurrency.core.execution;

import com.xjtu.iron.concurrency.api.enums.QueueType;
import com.xjtu.iron.concurrency.api.exception.ThreadPoolCreateException;
import com.xjtu.iron.concurrency.api.execution.pool.ThreadPoolSpec;
import com.xjtu.iron.concurrency.core.spi.RejectedExecutionHandlerFactory;
import com.xjtu.iron.concurrency.core.spi.ThreadPoolFactory;

import java.util.concurrent.*;

/**
 * 默认线程池工厂。
 *
 * <p>并行组件底层明确使用 JDK {@link ThreadPoolExecutor}，便于运行时诊断、指标采集和动态调整。</p>
 */
public class DefaultThreadPoolFactory implements ThreadPoolFactory {

    private final RejectedExecutionHandlerFactory rejectedExecutionHandlerFactory;

    public DefaultThreadPoolFactory(RejectedExecutionHandlerFactory rejectedExecutionHandlerFactory) {
        this.rejectedExecutionHandlerFactory = rejectedExecutionHandlerFactory;
    }

    @Override
    public ThreadPoolExecutor create(ThreadPoolSpec spec) {
        try {
            spec.validate();

            BlockingQueue<Runnable> queue = createQueue(spec);
            ThreadFactory threadFactory = new NamedThreadFactory(spec.getThreadNamePrefix());
            RejectedExecutionHandler rejectedExecutionHandler = rejectedExecutionHandlerFactory.create(spec);

            ThreadPoolExecutor executor = new ThreadPoolExecutor(
                    spec.getCorePoolSize(),
                    spec.getMaxPoolSize(),
                    spec.getKeepAliveTime().toMillis(),
                    TimeUnit.MILLISECONDS,
                    queue,
                    threadFactory,
                    rejectedExecutionHandler
            );

            executor.allowCoreThreadTimeOut(spec.isAllowCoreThreadTimeout());
            return executor;
        } catch (Exception ex) {
            throw new ThreadPoolCreateException(spec == null ? "unknown" : spec.getName(), ex);
        }
    }

    private BlockingQueue<Runnable> createQueue(ThreadPoolSpec spec) {
        QueueType queueType = spec.getQueueType();
        if (queueType == QueueType.BOUNDED_ARRAY_BLOCKING_QUEUE) {
            return new ArrayBlockingQueue<>(spec.getQueueCapacity());
        }
        if (queueType == QueueType.DIRECT_HANDOFF) {
            return new SynchronousQueue<>();
        }
        return new LinkedBlockingQueue<>(spec.getQueueCapacity());
    }
}
