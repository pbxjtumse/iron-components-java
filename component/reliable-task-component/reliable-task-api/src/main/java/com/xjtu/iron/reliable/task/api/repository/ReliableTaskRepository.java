package com.xjtu.iron.reliable.task.api.repository;

import com.xjtu.iron.reliable.task.api.model.ReliableTask;
import com.xjtu.iron.reliable.task.api.model.ReliableTaskKey;
import com.xjtu.iron.reliable.task.api.repository.claim.ReliableTaskClaimCommand;
import com.xjtu.iron.reliable.task.api.repository.claim.ReliableTaskClaimResult;
import com.xjtu.iron.reliable.task.api.repository.create.ReliableTaskCreateResult;
import com.xjtu.iron.reliable.task.api.repository.lease.ReliableTaskLeaseRenewCommand;
import com.xjtu.iron.reliable.task.api.repository.scan.ReliableTaskScanQuery;
import com.xjtu.iron.reliable.task.api.repository.transition.ReliableTaskAdminTransitionCommand;
import com.xjtu.iron.reliable.task.api.repository.transition.ReliableTaskTransitionCommand;

import java.util.List;
import java.util.Optional;

/** Reliable Task 持久化端口。 */
public interface ReliableTaskRepository {

    ReliableTaskCreateResult create(ReliableTask task);

    Optional<ReliableTask> find(ReliableTaskKey key);

    /** 返回候选快照；返回结果本身不是执行许可。 */
    List<ReliableTask> findDue(ReliableTaskScanQuery query);

    /** 基于 candidate version、状态和时间进行原子抢占。 */
    ReliableTaskClaimResult tryClaim(ReliableTaskClaimCommand command);

    /** 只有当前 owner/version 仍有效时才能完成状态迁移。 */
    boolean transition(ReliableTaskTransitionCommand command);

    /** 续租不推进 version，但必须校验当前 owner/version。 */
    boolean renewLease(ReliableTaskLeaseRenewCommand command);

    /** 人工动作仍然必须使用观察到的 status/version 做 CAS。 */
    boolean adminTransition(ReliableTaskAdminTransitionCommand command);
}
