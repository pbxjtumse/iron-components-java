package com.xjtu.iron.governance.model.event;

/** 治理执行过程中发布的标准事件类型。 */
public enum GovernanceEventType {

    /** 一次受治理调用开始。 */
    CALL_STARTED,

    /** 受治理调用成功完成。 */
    CALL_SUCCEEDED,

    /** 受治理调用执行失败。 */
    CALL_FAILED,

    /** 受治理调用被限流、熔断或隔离策略拒绝。 */
    CALL_REJECTED,

    /** 受治理调用执行超时。 */
    CALL_TIMEOUT,

    /** 受治理调用触发了一次重试。 */
    CALL_RETRIED,

    /** 主调用失败后触发降级逻辑。 */
    FALLBACK_TRIGGERED,

    /** 降级逻辑自身执行失败。 */
    FALLBACK_FAILED,

    /** 熔断器进入打开状态。 */
    CIRCUIT_OPENED,

    /** 熔断器进入半开探测状态。 */
    CIRCUIT_HALF_OPEN,

    /** 熔断器恢复为关闭状态。 */
    CIRCUIT_CLOSED,

    /** 治理规则加载成功。 */
    RULE_LOADED,

    /** 已加载的治理规则发生变更。 */
    RULE_CHANGED,

    /** 治理规则加载失败。 */
    RULE_LOAD_FAILED
}
