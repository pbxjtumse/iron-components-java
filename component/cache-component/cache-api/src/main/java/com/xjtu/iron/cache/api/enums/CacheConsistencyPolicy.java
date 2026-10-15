package com.xjtu.iron.cache.api.enums;

/**
 * CacheConsistencyPolicy 枚举。
 *
 * <p>用于描述缓存组件中的固定策略或状态，避免业务代码使用散乱字符串。</p>
 */
public enum CacheConsistencyPolicy {

    /** 只保证基本缓存可用性，允许较长时间的旧值。 */
    WEAK,

    /** 通过失效或事件传播使多级缓存最终收敛。 */
    EVENTUAL,

    /** 通过同步失效等手段缩短不一致窗口，但不承诺强一致。 */
    NEAR_STRONG,

    /** 要求缓存与源数据保持强一致语义。 */
    STRONG
}
