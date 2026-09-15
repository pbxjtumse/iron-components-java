package com.xjtu.iron.concurrency.core.rejection;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 拒绝感知版 DISCARD 策略。
 *
 * <p>
 * 任务不会执行，也不会向 {@code execute(...)} 的提交方同步抛异常；但组件会把任务标记为
 * REJECTED，并让对应 CompletableFuture 异常完成，避免 Future 永久等待。
 * </p>
 *
 * <p>
 * 这与 JDK 静默丢弃不同。对于 fire-and-forget 调用，提交方仍不会收到同步异常，
 * 但指标、监听器和任务状态注册表能够感知该次丢弃。
 * </p>
 */
public final class DiscardRejectedExecutionHandler implements RejectedExecutionHandler {

    @Override
    public void rejectedExecution(
            Runnable runnable,
            ThreadPoolExecutor executor
    ) {
        RejectedTaskSupport.reject(
                runnable,
                executor.isShutdown()
                        ? "Executor already shutdown; task discarded"
                        : "Task discarded by reject-aware DISCARD policy"
        );
    }
}