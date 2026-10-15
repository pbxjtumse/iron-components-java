package com.xjtu.iron.governance.model.resource;

/** 被治理资源在调用链中的类型。 */
public enum GovernanceResourceType {

    /**
     * 出站调用：调用下游服务、外部接口。
     */
    OUTBOUND,

    /**
     * 入站保护：Controller / RPC Provider。
     * 一期预留，二期使用。
     */
    INBOUND,

    /**
     * 内部方法保护。
     */
    INTERNAL,

    /**
     * 定时任务。
     */
    JOB,

    /**
     * MQ 消费。
     */
    MQ_CONSUMER
}
