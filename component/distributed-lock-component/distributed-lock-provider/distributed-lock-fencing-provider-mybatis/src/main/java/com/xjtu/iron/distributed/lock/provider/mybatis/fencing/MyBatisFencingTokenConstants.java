package com.xjtu.iron.distributed.lock.provider.mybatis.fencing;

/** MyBatis fencing token Provider 常量。 */
public final class MyBatisFencingTokenConstants {

    /** 对外稳定的 Provider 名称。 */
    public static final String PROVIDER_NAME = "mybatis-sequence";

    /** 默认技术表名。 */
    public static final String DEFAULT_TABLE_NAME = "iron_lock_fencing_token";

    /** 并发首次插入冲突的默认重试次数。 */
    public static final int DEFAULT_MAX_RETRIES = 5;

    /** namespace 最大长度。 */
    public static final int MAX_NAMESPACE_LENGTH = 128;

    /** lockName 最大长度。 */
    public static final int MAX_LOCK_NAME_LENGTH = 512;

    private MyBatisFencingTokenConstants() {
    }
}
