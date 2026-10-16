package com.xjtu.iron.idempotent.core.failure;

import com.xjtu.iron.idempotent.api.repository.write.IdempotencyFailureInfo;
import com.xjtu.iron.idempotent.api.spi.IdempotencyFailureClassifier;

import java.time.Instant;

/**
 * 默认失败分类器。
 *
 * <p>采用保守策略：所有业务异常默认都标记为不可恢复。
 * 原因是基础组件无法判断“再次执行业务”是否安全。支付、发券、扣减库存等副作用操作
 * 必须由业务或集成模块显式提供更精确的 {@link IdempotencyFailureClassifier}。</p>
 */
public final class DefaultIdempotencyFailureClassifier
        implements IdempotencyFailureClassifier {

    @Override
    public IdempotencyFailureInfo classify(Throwable error, Instant occurredAt) {
        String code = error == null
                ? "EXECUTION_FAILED"
                : error.getClass().getSimpleName();
        String message = error == null
                ? "execution failed"
                : error.getMessage();

        // 默认 false：宁可让 Reliable Task 暂停，也不要错误地重复执行有副作用业务。
        return new IdempotencyFailureInfo(code, message, false, occurredAt);
    }
}
