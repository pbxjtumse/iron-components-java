package com.xjtu.iron.concurrency.core.rejection;

import com.xjtu.iron.concurrency.core.task.RejectedTaskAware;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 拒绝任务通知工具。
 *
 * <p>
 * JDK 的拒绝策略只关心 Runnable 是否被接受；并行组件还需要把拒绝结果同步到TaskCommand 对应的 CompletableFuture、任务状态注册表、指标和监听器。
 * </p>
 */
final class RejectedTaskSupport {

    private RejectedTaskSupport() {
    }

    /**
     * 任务被自定义拒绝策略直接放入队列后，
     * 再次检查线程池是否已经并发关闭。
     *
     * <p>
     * 自定义拒绝策略直接调用 queue.offer(...) 时，
     * 会绕过 ThreadPoolExecutor.execute(...) 内部的入队后二次状态检查。
     * </p>
     *
     * @param runnable 刚刚入队的任务
     * @param executor 当前线程池
     * @param message 拒绝说明
     */
    static void rejectIfShutdownAfterEnqueue(Runnable runnable, ThreadPoolExecutor executor, String message) {
        /*
         * 线程池没有关闭，不需要处理。
         */
        if (!executor.isShutdown()) {
            return;
        }

        /*
         * 如果任务仍在队列中，成功将其撤回。
         *
         * remove 返回 true：
         *   任务尚未开始执行，当前线程成功撤回，因此明确拒绝。
         *
         * remove 返回 false：
         *   只能说明任务已经不在队列中。可能已经被工作线程取走，也可能被 shutdownNow、取消逻辑或其他并发路径移除。
         */
        if (executor.remove(runnable)) {
            throw reject(runnable, message);
        }
    }

    /**
     * 创建拒绝异常并通知支持拒绝感知的任务。
     *
     * @param runnable 被拒绝的任务
     * @param message 拒绝说明
     * @return 已完成拒绝通知的异常对象
     */
    static RejectedExecutionException reject(Runnable runnable, String message) {
        return reject(runnable, message, null);
    }

    /**
     * 创建拒绝异常并通知支持拒绝感知的任务。
     *
     * @param runnable 被拒绝的任务
     * @param message 拒绝说明
     * @param cause 原始异常
     * @return 已完成拒绝通知的异常对象
     */
    static RejectedExecutionException reject(Runnable runnable, String message, Throwable cause) {
        RejectedExecutionException exception = cause == null
                ? new RejectedExecutionException(message)
                : new RejectedExecutionException(message, cause);

        if (runnable instanceof RejectedTaskAware rejectedTaskAware) {
            try {
                rejectedTaskAware.reject(exception);
            } catch (RuntimeException notificationError) {
                /*
                 * 拒绝状态通知失败不能覆盖最初的拒绝语义，使用 suppressed 保留诊断信息。
                 */
                exception.addSuppressed(notificationError);
            }
        }

        return exception;
    }
}
