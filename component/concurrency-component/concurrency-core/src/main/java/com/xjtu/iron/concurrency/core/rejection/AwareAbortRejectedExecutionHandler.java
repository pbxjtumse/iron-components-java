package com.xjtu.iron.concurrency.core.rejection;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 拒绝感知版 Abort 策略。
 *
 * <p>
 * 与 JDK AbortPolicy 一样直接抛出异常，同时会先通知 RejectedTaskAware，
 * 使 CompletableFuture 和任务状态不会永久停留在未完成状态。
 * </p>
 */
public final class AwareAbortRejectedExecutionHandler implements RejectedExecutionHandler {

    @Override
    public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
        throw RejectedTaskSupport.reject(
                runnable,
                executor.isShutdown()
                        ? "Executor already shutdown"
                        : "Task rejected by ABORT policy"
        );
    }
}
