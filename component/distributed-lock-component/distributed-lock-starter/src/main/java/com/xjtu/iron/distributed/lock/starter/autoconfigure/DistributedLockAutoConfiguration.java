package com.xjtu.iron.distributed.lock.starter.autoconfigure;

import com.xjtu.iron.distributed.lock.api.client.DistributedLockClient;
import com.xjtu.iron.distributed.lock.api.model.LockOptions;
import com.xjtu.iron.distributed.lock.core.acquire.*;
import com.xjtu.iron.distributed.lock.core.client.DefaultDistributedLockClient;
import com.xjtu.iron.distributed.lock.core.client.LockHandleFactory;
import com.xjtu.iron.distributed.lock.core.execute.LockExecutionTemplate;
import com.xjtu.iron.distributed.lock.core.execute.LockResultResolver;
import com.xjtu.iron.distributed.lock.core.fencing.coordinator.FencingTokenCoordinator;
import com.xjtu.iron.distributed.lock.core.fencing.flow.*;
import com.xjtu.iron.distributed.lock.core.fencing.registry.DefaultFencingTokenProviderRegistry;
import com.xjtu.iron.distributed.lock.core.fencing.registry.FencingTokenProviderRegistry;
import com.xjtu.iron.distributed.lock.core.observability.*;
import com.xjtu.iron.distributed.lock.core.registry.DefaultLockProviderRegistry;
import com.xjtu.iron.distributed.lock.core.support.*;
import com.xjtu.iron.distributed.lock.core.wait.LockWaiterFactory;
import com.xjtu.iron.distributed.lock.core.watchdog.LockWatchdog;
import com.xjtu.iron.distributed.lock.core.watchdog.ScheduledLockWatchdog;
import com.xjtu.iron.distributed.lock.spi.LockProvider;
import com.xjtu.iron.distributed.lock.spi.LockProviderRegistry;
import com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenProvider;
import com.xjtu.iron.distributed.lock.starter.observability.MicrometerLockMetricsRecorder;
import com.xjtu.iron.distributed.lock.starter.observability.SpringLockEventPublisher;
import com.xjtu.iron.distributed.lock.starter.properties.DistributedLockProperties;
import com.xjtu.iron.distributed.lock.starter.properties.JdbcFencingTokenProperties;
import com.xjtu.iron.distributed.lock.starter.properties.RedissonDistributedLockProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.util.List;

/** 分布式锁核心自动配置。 */
@AutoConfiguration(after = {RedisDistributedLockAutoConfiguration.class, RedissonDistributedLockAutoConfiguration.class,
        JdbcFencingTokenAutoConfiguration.class})
@EnableConfigurationProperties({DistributedLockProperties.class, JdbcFencingTokenProperties.class, RedissonDistributedLockProperties.class})
@ConditionalOnProperty(prefix = "xjtu.iron.distributed-lock", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DistributedLockAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OwnerTokenGenerator ownerTokenGenerator() { return new DefaultOwnerTokenGenerator(); }

    @Bean
    @ConditionalOnMissingBean
    public LockWaiterFactory lockWaiterFactory() { return new LockWaiterFactory(); }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public LockWatchdog lockWatchdog(ObjectProvider<Clock> clockProvider) {
        return new ScheduledLockWatchdog(clockProvider.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnMissingBean
    public LockNameValidator lockNameValidator() { return new DefaultLockNameValidator(); }

    @Bean
    @ConditionalOnMissingBean
    public LockNamePatternResolver lockNamePatternResolver() { return new DefaultLockNamePatternResolver(); }

    @Bean
    @ConditionalOnMissingBean
    public LockEventPublisher lockEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        return new SpringLockEventPublisher(applicationEventPublisher);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(MeterRegistry.class)
    public LockMetricsRecorder micrometerLockMetricsRecorder(MeterRegistry meterRegistry) {
        return new MicrometerLockMetricsRecorder(meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(LockMetricsRecorder.class)
    public LockMetricsRecorder noOpLockMetricsRecorder() { return new NoOpLockMetricsRecorder(); }

    @Bean
    @ConditionalOnMissingBean
    public LockEventFactory lockEventFactory() { return new LockEventFactory(); }

    @Bean
    @ConditionalOnMissingBean
    public LockMetricsFacade lockMetricsFacade(LockMetricsRecorder metricsRecorder, LockNamePatternResolver patternResolver) {
        return new LockMetricsFacade(metricsRecorder, patternResolver);
    }

    @Bean(name = "distributedLockDefaultOptions")
    @ConditionalOnMissingBean(name = "distributedLockDefaultOptions")
    public LockOptions distributedLockDefaultOptions(DistributedLockProperties properties) {
        return properties.toLockOptions();
    }

    @Bean
    @ConditionalOnBean(LockProvider.class)
    @ConditionalOnMissingBean
    public LockProviderRegistry lockProviderRegistry(List<LockProvider> providers, DistributedLockProperties properties) {
        return new DefaultLockProviderRegistry(properties.getDefaultProvider(), providers);
    }


    @Bean
    @ConditionalOnMissingBean
    public FencingTokenProviderRegistry fencingTokenProviderRegistry(List<FencingTokenProvider> providers) {
        /*
         * Registry 只维护“名称 -> Provider”映射，不在这里推导默认 external fencing Provider。
         * 真正的选择由 FencingTokenCoordinator 根据 LockOptions 显式决定。
         */
        return new DefaultFencingTokenProviderRegistry(providers);
    }

    @Bean
    @ConditionalOnMissingBean
    public FencingTokenCoordinator fencingTokenCoordinator(FencingTokenProviderRegistry registry) {
        return new FencingTokenCoordinator(registry);
    }

    @Bean
    @ConditionalOnMissingBean
    public FencingTokenFlowSupport fencingTokenFlowSupport(FencingTokenCoordinator fencingTokenCoordinator, LockEventPublisher eventPublisher,
            LockEventFactory eventFactory, LockMetricsFacade metricsFacade, ObjectProvider<Clock> clockProvider) {
        return new FencingTokenFlowSupport(fencingTokenCoordinator, eventPublisher, eventFactory, metricsFacade,
                clockProvider.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnMissingBean(NoFencingTokenFlow.class)
    public NoFencingTokenFlow noFencingTokenFlow() { return new NoFencingTokenFlow(); }

    @Bean
    @ConditionalOnMissingBean(NativeFencingTokenFlow.class)
    public NativeFencingTokenFlow nativeFencingTokenFlow(FencingTokenFlowSupport support) {
        return new NativeFencingTokenFlow(support);
    }

    @Bean
    @ConditionalOnMissingBean(ExternalFencingTokenFlow.class)
    public ExternalFencingTokenFlow externalFencingTokenFlow(FencingTokenFlowSupport support) {
        return new ExternalFencingTokenFlow(support);
    }

    @Bean
    @ConditionalOnMissingBean
    public FencingTokenFlowRegistry fencingTokenFlowRegistry(List<FencingTokenFlow> flows) {
        return new DefaultFencingTokenFlowRegistry(flows);
    }

    @Bean
    @ConditionalOnMissingBean
    public LockHandleFactory lockHandleFactory(LockEventPublisher eventPublisher, LockEventFactory eventFactory, LockMetricsFacade metricsFacade,
            LockWatchdog watchdog) {
        return new LockHandleFactory(eventPublisher, eventFactory, metricsFacade, watchdog);
    }

    @Bean
    @ConditionalOnMissingBean(AcquiredLockAcquireOutcomeHandler.class)
    public AcquiredLockAcquireOutcomeHandler acquiredLockAcquireOutcomeHandler(FencingTokenFlowRegistry flowRegistry,
            LockHandleFactory lockHandleFactory, LockEventPublisher eventPublisher, LockEventFactory eventFactory, LockMetricsFacade metricsFacade) {
        return new AcquiredLockAcquireOutcomeHandler(flowRegistry, lockHandleFactory, eventPublisher, eventFactory, metricsFacade);
    }

    @Bean
    @ConditionalOnMissingBean(NotAcquiredLockAcquireOutcomeHandler.class)
    public NotAcquiredLockAcquireOutcomeHandler notAcquiredLockAcquireOutcomeHandler(LockEventPublisher eventPublisher, LockEventFactory eventFactory,
            LockMetricsFacade metricsFacade) {
        return new NotAcquiredLockAcquireOutcomeHandler(eventPublisher, eventFactory, metricsFacade);
    }

    @Bean
    @ConditionalOnMissingBean(ProviderErrorLockAcquireOutcomeHandler.class)
    public ProviderErrorLockAcquireOutcomeHandler providerErrorLockAcquireOutcomeHandler(LockEventPublisher eventPublisher,
            LockEventFactory eventFactory, LockMetricsFacade metricsFacade) {
        return new ProviderErrorLockAcquireOutcomeHandler(eventPublisher, eventFactory, metricsFacade);
    }

    @Bean
    @ConditionalOnMissingBean
    public LockAcquireOutcomeHandlerRegistry lockAcquireOutcomeHandlerRegistry(List<LockAcquireOutcomeHandler> handlers) {
        return new DefaultLockAcquireOutcomeHandlerRegistry(handlers);
    }

    @Bean
    @ConditionalOnMissingBean
    public LockResultResolver lockResultResolver() {
        return new LockResultResolver();
    }

    @Bean
    @ConditionalOnBean(LockProviderRegistry.class)
    @ConditionalOnMissingBean
    public LockAcquisitionService lockAcquisitionService(LockProviderRegistry providerRegistry, OwnerTokenGenerator ownerTokenGenerator,
            LockWaiterFactory waiterFactory, LockEventPublisher eventPublisher, LockEventFactory eventFactory, LockNameValidator lockNameValidator,
            @Qualifier("distributedLockDefaultOptions") LockOptions defaultOptions, FencingTokenCoordinator fencingTokenCoordinator,
            LockAcquireOutcomeHandlerRegistry acquireOutcomeHandlerRegistry, ObjectProvider<Clock> clockProvider) {
        return new LockAcquisitionService(providerRegistry, ownerTokenGenerator, waiterFactory, eventPublisher, eventFactory, lockNameValidator,
                defaultOptions, clockProvider.getIfAvailable(Clock::systemUTC), fencingTokenCoordinator, acquireOutcomeHandlerRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public LockExecutionTemplate lockExecutionTemplate(LockAcquisitionService acquisitionService, LockEventPublisher eventPublisher,
            LockEventFactory eventFactory, LockMetricsFacade metricsFacade, LockResultResolver resultResolver, ObjectProvider<Clock> clockProvider) {
        return new LockExecutionTemplate(acquisitionService, eventPublisher, eventFactory, metricsFacade,
                clockProvider.getIfAvailable(Clock::systemUTC), resultResolver);
    }

    @Bean
    @ConditionalOnBean(LockProviderRegistry.class)
    @ConditionalOnMissingBean
    public DistributedLockClient distributedLockClient(LockAcquisitionService acquisitionService, LockExecutionTemplate executionTemplate) {
        return new DefaultDistributedLockClient(acquisitionService, executionTemplate);
    }
}
