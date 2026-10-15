package com.xjtu.iron.governance.model.engine;

/** 治理能力的底层引擎类型。 */
public enum GovernanceEngineType {

    /** 使用 Resilience4j 作为进程内治理引擎。 */
    RESILIENCE4J,

    /** 使用 Sentinel 作为流量治理引擎。 */
    SENTINEL
}
