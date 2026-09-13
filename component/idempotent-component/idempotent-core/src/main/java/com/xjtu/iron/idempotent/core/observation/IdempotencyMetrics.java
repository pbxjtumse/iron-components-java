package com.xjtu.iron.idempotent.core.observation;

import com.xjtu.iron.idempotent.api.execution.IdempotencyResultStatus;
import com.xjtu.iron.idempotent.api.policy.IdempotencyMode;

import java.time.Duration;

/**
 * Core 对指标系统的最小抽象。
 *
 * <p>Core 不依赖 Micrometer；Starter 可以把该 SPI 适配到 Micrometer。
 * 指标标签只能使用 mode / repository / status 等低基数维度，
 * 禁止把 idempotencyKey、routeKey、ownerToken 等高基数值作为标签。</p>
 */
public interface IdempotencyMetrics {

    /** 记录 Repository 状态抢占的决策结果。 */
    void recordAcquire(IdempotencyMode mode, String repository, String status);

    /** 记录一次完整 Executor 调用的最终状态和耗时。 */
    void recordExecution(IdempotencyMode mode, String repository, IdempotencyResultStatus status, Duration duration);

    static IdempotencyMetrics noop() {
        return new IdempotencyMetrics() {
            @Override
            public void recordAcquire(IdempotencyMode mode, String repository, String status) {
                // no-op
            }

            @Override
            public void recordExecution(IdempotencyMode mode, String repository, IdempotencyResultStatus status, Duration duration) {
                // no-op
            }
        };
    }
}
