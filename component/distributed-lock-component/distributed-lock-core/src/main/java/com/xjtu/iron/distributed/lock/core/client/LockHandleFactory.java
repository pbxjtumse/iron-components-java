package com.xjtu.iron.distributed.lock.core.client;

import com.xjtu.iron.distributed.lock.api.model.LockOptions;
import com.xjtu.iron.distributed.lock.core.observability.LockEventFactory;
import com.xjtu.iron.distributed.lock.core.observability.LockEventPublisher;
import com.xjtu.iron.distributed.lock.core.observability.LockMetricsFacade;
import com.xjtu.iron.distributed.lock.core.watchdog.LockWatchdog;
import com.xjtu.iron.distributed.lock.spi.LockProvider;
import com.xjtu.iron.distributed.lock.spi.protocol.common.LockLease;

import java.util.Objects;

/**
 * {@link DefaultLockHandle} 创建器。
 *
 * <p>Factory 不是为了“套设计模式”，而是为了把 Handle 构造时必须注入的运行态、事件、指标、watchdog 依赖集中起来。acquire 成功后只需要
 * 调用本类，就能得到一个已经具备运行态管理能力的 Handle；调用方不需要知道 {@link LockRuntimeState}、事件和指标如何组装。</p>
 */
public final class LockHandleFactory {

    private final LockEventPublisher eventPublisher;
    private final LockEventFactory eventFactory;
    private final LockMetricsFacade metricsFacade;
    private final LockWatchdog watchdog;

    public LockHandleFactory(LockEventPublisher eventPublisher, LockEventFactory eventFactory, LockMetricsFacade metricsFacade, LockWatchdog watchdog) {
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.eventFactory = Objects.requireNonNull(eventFactory, "eventFactory must not be null");
        this.metricsFacade = Objects.requireNonNull(metricsFacade, "metricsFacade must not be null");
        this.watchdog = Objects.requireNonNull(watchdog, "watchdog must not be null");
    }

    /**
     * 创建一次成功租约对应的本地 Handle。
     *
     * <p>autoRenew 的启动点放在这里，而不是 execute 模板中。原因是 {@code tryLock(autoRenew=true)} 和
     * {@code execute(autoRenew=true)} 都会经过“acquire 成功 -> 创建 Handle”这个公共节点。这样两种 API 的自动续期生命周期完全一致，
     * 不会出现 execute 能续期、手工 tryLock 不能续期的语义差异。</p>
     */
    public DefaultLockHandle create(LockProvider provider, LockLease lease, LockOptions options) {
        DefaultLockHandle handle = new DefaultLockHandle(Objects.requireNonNull(provider, "provider must not be null"),
                Objects.requireNonNull(lease, "lease must not be null"), new LockRuntimeState(), eventPublisher, eventFactory, metricsFacade, watchdog);
        if (Objects.requireNonNull(options, "options must not be null").isAutoRenew()) {
            watchdog.start(handle, options);
        }
        return handle;
    }
}
