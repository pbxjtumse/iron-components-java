package com.xjtu.iron.concurrency.core.execution;

import com.xjtu.iron.concurrency.api.enums.RejectionPolicy;
import com.xjtu.iron.concurrency.api.execution.pool.ThreadPoolSpec;
import com.xjtu.iron.concurrency.core.rejection.*;
import com.xjtu.iron.concurrency.core.spi.RejectedExecutionHandlerFactory;

import java.util.Objects;
import java.util.concurrent.RejectedExecutionHandler;

/**
 * 默认拒绝策略工厂。
 *
 * <p>
 * 工厂只负责根据配置选择处理器，具体行为由 execution.rejection 包中的独立类实现。
 * </p>
 */
public final class DefaultRejectedExecutionHandlerFactory implements RejectedExecutionHandlerFactory {

    @Override
    public RejectedExecutionHandler create(ThreadPoolSpec spec) {
        Objects.requireNonNull(spec, "spec must not be null");

        RejectionPolicy policy = spec.getRejectionPolicy() == null ? RejectionPolicy.ABORT : spec.getRejectionPolicy();

        return switch (policy) {
            case ABORT -> new AwareAbortRejectedExecutionHandler();
            case CALLER_RUNS -> new CallerRunsRejectedExecutionHandler();
            case DISCARD -> new DiscardRejectedExecutionHandler();
            case DISCARD_OLDEST -> new DiscardOldestRejectedExecutionHandler();
            case BLOCKING_WAIT -> new BlockingWaitRejectedExecutionHandler(spec.getRejectionWaitTime());
        };
    }
}
