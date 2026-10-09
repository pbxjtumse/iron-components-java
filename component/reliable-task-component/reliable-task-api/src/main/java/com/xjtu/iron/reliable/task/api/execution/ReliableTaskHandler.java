package com.xjtu.iron.reliable.task.api.execution;

/** 一种持久化任务类型的业务执行器。 */
public interface ReliableTaskHandler {

    /** 稳定、低基数且全局唯一的任务类型。 */
    String taskType();

    /** 执行一轮任务并返回显式业务结果；未处理异常由 Core 转换为延迟重试。 */
    ReliableTaskExecutionResult execute(ReliableTaskExecutionContext context) throws Exception;
}
