package com.xjtu.iron.reliable.task.api.client;

import com.xjtu.iron.reliable.task.api.model.ReliableTask;
import com.xjtu.iron.reliable.task.api.model.ReliableTaskKey;
import com.xjtu.iron.reliable.task.api.model.ReliableTaskSubmission;

import java.util.Optional;

/** 提交、快速执行和查询任务的稳定门面。 */
public interface ReliableTaskClient {

    ReliableTaskSubmitResult submit(ReliableTaskSubmission submission);

    /** 快速路径；仍然必须先通过与扫描器相同的 CAS 抢占。 */
    ReliableTaskRunResult runNow(ReliableTaskKey key);

    Optional<ReliableTask> find(ReliableTaskKey key);
}
