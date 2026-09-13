package com.xjtu.iron.distributed.lock.core.acquire;

import com.xjtu.iron.distributed.lock.api.model.LockHandle;
import com.xjtu.iron.distributed.lock.api.model.LockResult;
import com.xjtu.iron.distributed.lock.spi.protocol.acquire.LockAcquireStatus;

import java.util.*;

/**
 * 默认 acquire 结果处理器注册表。
 *
 * <p>注册阶段同时校验重复处理器和状态覆盖完整性。以后新增
 * {@link LockAcquireStatus} 时，只需要增加对应 Handler Bean；
 * {@code DefaultDistributedLockClient} 不需要再修改状态分发代码。</p>
 */
public final class DefaultLockAcquireOutcomeHandlerRegistry
        implements LockAcquireOutcomeHandlerRegistry {

    private final Map<LockAcquireStatus, LockAcquireOutcomeHandler> handlers;

    public DefaultLockAcquireOutcomeHandlerRegistry(Collection<? extends LockAcquireOutcomeHandler> handlers) {
        EnumMap<LockAcquireStatus, LockAcquireOutcomeHandler> mapped =
                new EnumMap<>(LockAcquireStatus.class);
        if (handlers != null) {
            for (LockAcquireOutcomeHandler handler : handlers) {
                Objects.requireNonNull(handler, "lock acquire outcome handler must not be null");
                LockAcquireStatus status = Objects.requireNonNull(handler.status(), "lock acquire outcome handler status must not be null");
                LockAcquireOutcomeHandler previous = mapped.putIfAbsent(status, handler);
                if (previous != null) {
                    throw new IllegalArgumentException("duplicate lock acquire outcome handler: " + status);
                }
            }
        }

        EnumSet<LockAcquireStatus> missing = EnumSet.allOf(LockAcquireStatus.class);
        missing.removeAll(mapped.keySet());
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("missing lock acquire outcome handler: " + missing);
        }
        this.handlers = Collections.unmodifiableMap(mapped);
    }

    @Override
    public Optional<LockAcquireOutcomeHandler> findHandler(LockAcquireStatus status) {
        if (status == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(handlers.get(status));
    }

    @Override
    public LockAcquireOutcomeHandler getRequired(LockAcquireStatus status) {
        return findHandler(status).orElseThrow(() -> new IllegalArgumentException(
                "lock acquire outcome handler not found: " + status));
    }

    @Override
    public LockResult<LockHandle> handle(LockAcquireOutcomeContext context) {
        Objects.requireNonNull(context, "context must not be null");
        return getRequired(context.response().getStatus()).handle(context);
    }

    @Override
    public Set<LockAcquireStatus> statuses() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(handlers.keySet()));
    }
}
