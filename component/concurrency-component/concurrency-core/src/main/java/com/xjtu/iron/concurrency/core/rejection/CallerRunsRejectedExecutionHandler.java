package com.xjtu.iron.concurrency.core.rejection;

import com.xjtu.iron.concurrency.core.task.CallerRunsAware;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 组件增强版 CALLER_RUNS 策略。
 *
 * <p>
 * 线程池仍在运行时，由调用 {@code executor.execute(...)} 的提交线程直接执行任务，
 * 并把执行方式记录为 CALLER_THREAD。线程池关闭后不再执行新任务，而是明确拒绝并抛异常。
 * </p>
 */
public final class CallerRunsRejectedExecutionHandler implements RejectedExecutionHandler {

    @Override
    public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
        if (executor.isShutdown()) {
            throw RejectedTaskSupport.reject(runnable, "Executor already shutdown; CALLER_RUNS cannot accept new task");
        }

        if (runnable instanceof CallerRunsAware callerRunsAware) {
            callerRunsAware.markCallerThreadExecution();
        }

        runnable.run();
    }
}
