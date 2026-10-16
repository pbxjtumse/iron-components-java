package com.xjtu.iron.concurrency.core.rejection;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 阻塞等待拒绝策略。
 *
 * <p>
 * 当线程池工作线程和队列都已饱和时，提交线程最多等待指定时间获取队列空位。
 * 等待超时、线程池关闭或提交线程被中断时，任务会被明确标记为拒绝。
 * </p>
 */
/**
 * 阻塞等待队列空位的拒绝策略。
 *
 * <p>
 * 当线程池饱和时，提交线程最多等待指定时间把任务放入队列；等待超时、线程被中断或线程池关闭时，
 * 通知任务进入 REJECTED 并向提交方抛出拒绝异常。
 * </p>
 */
public final class BlockingWaitRejectedExecutionHandler implements RejectedExecutionHandler {

    /**
     * 提交线程最多等待队列空位的时间。
     */
    private final Duration waitTime;

    public BlockingWaitRejectedExecutionHandler(Duration waitTime) {
        this.waitTime = waitTime == null ? Duration.ZERO : waitTime;
    }

    @Override
    public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
        if (executor.isShutdown()) {
            throw RejectedTaskSupport.reject(runnable, "Executor already shutdown");
        }

        try {
            BlockingQueue<Runnable> queue = executor.getQueue();
            boolean offered = queue.offer(runnable, waitTime.toMillis(), TimeUnit.MILLISECONDS);
            //若是入队失败
            if (!offered) {
                throw RejectedTaskSupport.reject(runnable, "Task rejected after waiting " + waitTime.toMillis() + " ms");
            }
            //offer 成功不等于一定合法。等待期间线程池可能已经关闭。
            RejectedTaskSupport.rejectIfShutdownAfterEnqueue(runnable, executor, "Executor shutdown while enqueuing replacement task");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw RejectedTaskSupport.reject(runnable, "Interrupted while waiting to enqueue task", interrupted);
        }
    }
}
