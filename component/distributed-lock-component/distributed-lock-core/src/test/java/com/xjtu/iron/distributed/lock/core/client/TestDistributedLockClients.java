package com.xjtu.iron.distributed.lock.core.client;

import com.xjtu.iron.distributed.lock.api.client.DistributedLockClient;
import com.xjtu.iron.distributed.lock.api.model.LockOptions;
import com.xjtu.iron.distributed.lock.core.acquire.*;
import com.xjtu.iron.distributed.lock.core.execute.LockExecutionTemplate;
import com.xjtu.iron.distributed.lock.core.execute.LockResultResolver;
import com.xjtu.iron.distributed.lock.core.fencing.coordinator.FencingTokenCoordinator;
import com.xjtu.iron.distributed.lock.core.fencing.flow.*;
import com.xjtu.iron.distributed.lock.core.fencing.registry.DefaultFencingTokenProviderRegistry;
import com.xjtu.iron.distributed.lock.core.observability.LockEventFactory;
import com.xjtu.iron.distributed.lock.core.observability.LockMetricsFacade;
import com.xjtu.iron.distributed.lock.core.observability.NoOpLockEventPublisher;
import com.xjtu.iron.distributed.lock.core.observability.NoOpLockMetricsRecorder;
import com.xjtu.iron.distributed.lock.core.registry.DefaultLockProviderRegistry;
import com.xjtu.iron.distributed.lock.core.support.DefaultLockNamePatternResolver;
import com.xjtu.iron.distributed.lock.core.support.DefaultLockNameValidator;
import com.xjtu.iron.distributed.lock.core.support.DefaultOwnerTokenGenerator;
import com.xjtu.iron.distributed.lock.core.wait.LockWaiterFactory;
import com.xjtu.iron.distributed.lock.core.watchdog.NoOpLockWatchdog;
import com.xjtu.iron.distributed.lock.spi.LockProvider;
import com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenProvider;

import java.time.Clock;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 测试侧装配工具，避免为了兼容测试在生产 Client 上保留大量构造函数。 */
final class TestDistributedLockClients {

    private TestDistributedLockClients() {
    }

    static DistributedLockClient create(LockProvider lockProvider) {
        return create(lockProvider, Collections.emptyList());
    }

    static DistributedLockClient create(LockProvider lockProvider, List<FencingTokenProvider> fencingProviders) {
        Clock clock = Clock.systemUTC();
        NoOpLockEventPublisher eventPublisher = new NoOpLockEventPublisher();
        LockEventFactory eventFactory = new LockEventFactory();
        LockMetricsFacade metricsFacade = new LockMetricsFacade(new NoOpLockMetricsRecorder(), new DefaultLockNamePatternResolver());

        FencingTokenCoordinator coordinator = new FencingTokenCoordinator(new DefaultFencingTokenProviderRegistry(fencingProviders));
        FencingTokenFlowSupport flowSupport = new FencingTokenFlowSupport(coordinator, eventPublisher, eventFactory, metricsFacade, clock);
        List<FencingTokenFlow> flows = Arrays.asList(new NoFencingTokenFlow(), new NativeFencingTokenFlow(flowSupport),
                new ExternalFencingTokenFlow(flowSupport));
        FencingTokenFlowRegistry flowRegistry = new DefaultFencingTokenFlowRegistry(flows);

        NoOpLockWatchdog watchdog = new NoOpLockWatchdog();
        LockHandleFactory handleFactory = new LockHandleFactory(eventPublisher, eventFactory, metricsFacade, watchdog);
        List<LockAcquireOutcomeHandler> handlers = Arrays.asList(
                new AcquiredLockAcquireOutcomeHandler(flowRegistry, handleFactory, eventPublisher, eventFactory, metricsFacade),
                new NotAcquiredLockAcquireOutcomeHandler(eventPublisher, eventFactory, metricsFacade),
                new ProviderErrorLockAcquireOutcomeHandler( eventPublisher, eventFactory, metricsFacade));

        LockAcquisitionService acquisitionService = new LockAcquisitionService(
                new DefaultLockProviderRegistry("redis", Collections.singletonList(lockProvider)), new DefaultOwnerTokenGenerator(),
                new LockWaiterFactory(), eventPublisher, eventFactory, new DefaultLockNameValidator(), LockOptions.defaults(), clock, coordinator,
                new DefaultLockAcquireOutcomeHandlerRegistry(handlers));

        LockExecutionTemplate executionTemplate = new LockExecutionTemplate(acquisitionService, eventPublisher, eventFactory, metricsFacade, clock,
                new LockResultResolver());

        return new DefaultDistributedLockClient(acquisitionService, executionTemplate);
    }
}
