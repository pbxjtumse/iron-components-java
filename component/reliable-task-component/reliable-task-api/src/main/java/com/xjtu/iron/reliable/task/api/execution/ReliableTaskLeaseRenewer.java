package com.xjtu.iron.reliable.task.api.execution;

import java.time.Duration;

/** 长任务显式延长当前 owner Lease 的入口。 */
@FunctionalInterface
public interface ReliableTaskLeaseRenewer {
    boolean renew(Duration extension);
}
