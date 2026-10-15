package com.xjtu.iron.governance.model.engine;

/** 治理引擎可以提供的标准能力。 */
public enum GovernanceEngineCapability {

    /** 调用超时控制。 */
    TIMEOUT,

    /** 失败调用重试。 */
    RETRY,

    /** 熔断器保护。 */
    CIRCUIT_BREAKER,

    /** 舱壁隔离与并发限制。 */
    BULKHEAD,

    /** 通用流量限速。 */
    RATE_LIMIT,

    /** 针对热点参数或热点资源的限流。 */
    HOT_KEY_RATE_LIMIT,

    /** 根据系统负载实施自适应保护。 */
    SYSTEM_PROTECTION,

    /** 对进入当前应用的请求实施入口保护。 */
    INBOUND_PROTECTION
}
