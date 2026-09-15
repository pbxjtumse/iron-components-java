package com.xjtu.iron.governance.starter.autoconfigure;


import com.xjtu.iron.governance.api.template.GovernanceTemplate;
import com.xjtu.iron.governance.config.api.GovernanceRuleRepository;
import com.xjtu.iron.governance.config.local.InMemoryGovernanceRuleRepository;
import com.xjtu.iron.governance.core.event.GovernanceEventBus;
import com.xjtu.iron.governance.core.event.LoggingGovernanceEventListener;
import com.xjtu.iron.governance.core.event.SimpleGovernanceEventBus;
import com.xjtu.iron.governance.core.exception.GovernanceExceptionMapperChain;
import com.xjtu.iron.governance.core.executor.DefaultGovernanceExecutor;
import com.xjtu.iron.governance.core.executor.GovernanceExecutor;
import com.xjtu.iron.governance.core.resolver.DefaultEffectiveGovernancePolicyResolver;
import com.xjtu.iron.governance.core.resolver.EffectiveGovernancePolicyResolver;
import com.xjtu.iron.governance.core.template.DefaultGovernanceTemplate;
import com.xjtu.iron.governance.core.timeout.CallTimeoutExecutor;
import com.xjtu.iron.governance.engine.resilience4j.Resilience4jExceptionMapper;
import com.xjtu.iron.governance.engine.resilience4j.Resilience4jGovernanceEngine;
import com.xjtu.iron.governance.integration.spring.aop.GovernedCallAspect;
import com.xjtu.iron.governance.model.rule.GovernanceRuleSet;
import com.xjtu.iron.governance.spi.engine.GovernanceEngine;
import com.xjtu.iron.governance.spi.event.GovernanceEventListener;
import com.xjtu.iron.governance.spi.exception.GovernanceExceptionMapper;
import com.xjtu.iron.governance.starter.properties.GovernanceProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@AutoConfiguration
@EnableConfigurationProperties(GovernanceProperties.class)
@ConditionalOnProperty(prefix = "xjtu.iron.governance", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GovernanceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GovernanceRuleRepository governanceRuleRepository(GovernanceProperties properties) {
        GovernanceRuleSet ruleSet = new GovernanceRuleSet();
        ruleSet.setDefaultPolicy(properties.getDefaultPolicy());
        ruleSet.setResources(properties.getResources());
        return new InMemoryGovernanceRuleRepository(ruleSet);
    }

    @Bean
    @ConditionalOnMissingBean
    public EffectiveGovernancePolicyResolver effectiveGovernancePolicyResolver(GovernanceRuleRepository repository) {
        return new DefaultEffectiveGovernancePolicyResolver(repository);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExecutorService governanceTimeoutExecutorService(GovernanceProperties properties) {
        var p = properties.getTimeoutExecutor();

        return new ThreadPoolExecutor(
                p.getCorePoolSize(),
                p.getMaxPoolSize(),
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(p.getQueueCapacity()),
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("governance-timeout-executor-" + thread.getId());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public CallTimeoutExecutor callTimeoutExecutor(ExecutorService governanceTimeoutExecutorService) {
        return new CallTimeoutExecutor(governanceTimeoutExecutorService);
    }

    @Bean
    @ConditionalOnMissingBean
    public GovernanceEngine governanceEngine(CallTimeoutExecutor callTimeoutExecutor) {
        return new Resilience4jGovernanceEngine(callTimeoutExecutor);
    }

    @Bean
    @ConditionalOnMissingBean
    public GovernanceExceptionMapper resilience4jExceptionMapper() {
        return new Resilience4jExceptionMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    public GovernanceExceptionMapperChain governanceExceptionMapperChain(List<GovernanceExceptionMapper> mappers) {
        return new GovernanceExceptionMapperChain(mappers);
    }

    @Bean
    @ConditionalOnMissingBean
    public GovernanceEventListener loggingGovernanceEventListener() {
        return new LoggingGovernanceEventListener();
    }

    @Bean
    @ConditionalOnMissingBean
    public GovernanceEventBus governanceEventBus(List<GovernanceEventListener> listeners) {
        return new SimpleGovernanceEventBus(listeners);
    }

    @Bean
    @ConditionalOnMissingBean
    public GovernanceExecutor governanceExecutor(EffectiveGovernancePolicyResolver resolver,
                                                 GovernanceEngine governanceEngine,
                                                 GovernanceExceptionMapperChain mapperChain,
                                                 GovernanceEventBus eventBus) {
        return new DefaultGovernanceExecutor(
                resolver,
                governanceEngine,
                mapperChain,
                eventBus
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public GovernanceTemplate governanceTemplate(GovernanceExecutor governanceExecutor) {
        return new DefaultGovernanceTemplate(governanceExecutor);
    }

    @Bean
    @ConditionalOnMissingBean
    public GovernedCallAspect governedCallAspect(GovernanceExecutor governanceExecutor) {
        return new GovernedCallAspect(governanceExecutor);
    }
}
