package com.xjtu.iron.concurrency.core.testfixture;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class TestExecutors {

    private TestExecutors() {
    }

    public static ThreadPoolExecutor singleThreadWithQueue(int queueCapacity) {
        return new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                namedThreadFactory("test-single-"),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    public static ThreadPoolExecutor saturatedExecutor(
            RejectedExecutionHandler handler
    ) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                namedThreadFactory("test-saturated-"),
                handler
        );

        CountDownLatch blocker = new CountDownLatch(1);

        executor.execute(() -> {
            try {
                blocker.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        executor.execute(() -> {
            // occupy queue
        });

        return executor;
    }

    public static ThreadPoolExecutor shutdownExecutor() {
        ThreadPoolExecutor executor = singleThreadWithQueue(1);
        executor.shutdown();
        return executor;
    }

    public static ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger index = new AtomicInteger();

        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(prefix + index.incrementAndGet());
            return thread;
        };
    }

    public static void shutdownNow(ThreadPoolExecutor executor) {
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
