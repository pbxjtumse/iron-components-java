package com.xjtu.iron.domain.customer;

/**
 * CustomerType
 *
 * @author Frank Zhang
 * @date 2018-01-08 8:51 AM
 */
public enum CustomerType {

    /** 潜在客户，尚未表现出明确合作意向。 */
    POTENTIAL,

    /** 意向客户，已表现出较明确的合作意向。 */
    INTENTIONAL,

    /** 重要客户，需要优先跟进和服务。 */
    IMPORTANT,

    /** VIP 客户，使用最高级别的客户服务策略。 */
    VIP;
}
