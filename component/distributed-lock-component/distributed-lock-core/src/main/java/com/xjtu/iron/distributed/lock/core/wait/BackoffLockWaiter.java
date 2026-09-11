package com.xjtu.iron.distributed.lock.core.wait;

import com.xjtu.iron.distributed.lock.api.RetryBackoffSpec;
import com.xjtu.iron.distributed.lock.api.model.LockOptions;
import com.xjtu.iron.distributed.lock.spi.protocol.acquire.LockAcquireResponse;

import java.time.Duration;
import java.time.Instant;

/**
 * 指数退避等待策略。
 *
 * <p>该实现用于短等待场景。抢锁失败后按照 RetryBackoffSpec 休眠，并在 waitTime 截止前持续尝试。
 * 后续正式实现可以把 Sleeper 抽象出来，便于单元测试。</p>
 */
public final class BackoffLockWaiter implements LockWaiter {

    @Override
    public LockAcquireResponse waitForLock(LockWaitContext context) {
        LockOptions options = context.getRequest().getOptions();
        RetryBackoffSpec backoffSpec = options.getBackoffSpec();
        Instant deadline = Instant.now(context.getClock()).plus(options.getWaitTime());
        int attempt = 0;
        LockAcquireResponse lastResponse = null;
        while (Instant.now(context.getClock()).isBefore(deadline) || attempt == 0) {
            attempt++;
            //尝试获取锁的关键信息
            lastResponse = context.getProvider().acquire(context.getRequest());
            if (lastResponse.isAcquired() || lastResponse.hasError()) {
                return lastResponse;
            }
            Duration delay = backoffSpec.nextDelay(attempt);
            if (Instant.now(context.getClock()).plus(delay).isAfter(deadline)) {
                break;
            }
            sleep(delay);
        }
        return lastResponse == null ? context.getProvider().acquire(context.getRequest()) : lastResponse;
    }

    private static void sleep(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
