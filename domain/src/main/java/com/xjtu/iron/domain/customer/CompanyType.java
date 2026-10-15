package com.xjtu.iron.domain.customer;

/**
 * CompanyType
 *
 * @author Frank Zhang
 * @date 2018-01-08 11:02 AM
 */
public enum CompanyType {

    /** 潜在企业客户，尚未表现出明确合作意向。 */
    POTENTIAL,

    /** 意向企业客户，已表现出较明确的合作意向。 */
    INTENTIONAL,

    /** 重要企业客户，需要优先跟进和服务。 */
    IMPORTANT,

    /** VIP 企业客户，使用最高级别的客户服务策略。 */
    VIP;
}
