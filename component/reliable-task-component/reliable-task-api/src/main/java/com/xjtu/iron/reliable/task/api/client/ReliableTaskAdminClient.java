package com.xjtu.iron.reliable.task.api.client;

import com.xjtu.iron.reliable.task.api.model.ReliableTaskKey;

import java.time.Instant;

/** 后续管理接口或控制台可以直接复用的最小人工操作门面。 */
public interface ReliableTaskAdminClient {

    /** MANUAL/DEAD 重新进入 READY，并重置持久化执行次数。 */
    ReliableTaskAdminResult requeue(ReliableTaskKey key, long expectedVersion, Instant executeAt);

    /** 取消尚未运行的自动任务；不强行中断 RUNNING Handler。 */
    ReliableTaskAdminResult cancel(ReliableTaskKey key, long expectedVersion);
}
