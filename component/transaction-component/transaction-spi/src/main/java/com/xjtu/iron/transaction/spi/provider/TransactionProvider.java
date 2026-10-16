package com.xjtu.iron.transaction.spi.provider;

import com.xjtu.iron.transaction.api.definition.TransactionOptions;

/**
 * 底层本地事务基础设施适配 SPI。
 *
 * <p>SPI 使用“配置 + callback”的整体事务边界，而不是向 core 暴露 begin/commit/rollback/suspend/resume
 * 等低层动作。这样 REQUIRED、REQUIRES_NEW、MANDATORY 的真正传播行为始终由具体事务框架负责。</p>
 */
public interface TransactionProvider {

    /**
     * 按指定事务配置建立或复用事务边界，并在该边界内执行 callback。
     *
     * <p>正常完成直接返回 callback 的业务结果；业务异常保持原类型向上传递；
     * BEGIN/COMMIT/ROLLBACK 等基础设施异常使用 ProviderTransactionException 表达。</p>
     */
    <T> T execute(
            TransactionOptions options,
            TransactionProviderCallback<T> callback);
}
