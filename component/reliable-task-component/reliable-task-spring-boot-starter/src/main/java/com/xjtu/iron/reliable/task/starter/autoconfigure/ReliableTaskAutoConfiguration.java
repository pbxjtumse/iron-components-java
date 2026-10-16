package com.xjtu.iron.reliable.task.starter.autoconfigure;

import com.xjtu.iron.foundation.id.api.StringIdGenerator;
import com.xjtu.iron.foundation.id.factory.IdGenerators;
import com.xjtu.iron.reliable.task.api.client.ReliableTaskAdminClient;
import com.xjtu.iron.reliable.task.api.client.ReliableTaskClient;
import com.xjtu.iron.reliable.task.api.execution.ReliableTaskHandler;
import com.xjtu.iron.reliable.task.api.repository.ReliableTaskRepository;
import com.xjtu.iron.reliable.task.api.scan.ReliableTaskScanner;
import com.xjtu.iron.reliable.task.core.client.DefaultReliableTaskAdminClient;
import com.xjtu.iron.reliable.task.core.client.DefaultReliableTaskClient;
import com.xjtu.iron.reliable.task.core.execution.ReliableTaskEngine;
import com.xjtu.iron.reliable.task.core.execution.handler.DefaultReliableTaskHandlerRegistry;
import com.xjtu.iron.reliable.task.core.execution.handler.ReliableTaskHandlerRegistry;
import com.xjtu.iron.reliable.task.core.policy.ReliableTaskRuntimePolicy;
import com.xjtu.iron.reliable.task.core.scan.DefaultReliableTaskScanner;
import com.xjtu.iron.reliable.task.starter.properties.ReliableTaskProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** Reliable Task 的 Spring Boot 自动装配。 */
@AutoConfiguration(afterName = {
        "com.xjtu.iron.reliable.task.starter.autoconfigure.ReliableTaskMyBatisProviderAutoConfiguration"
})
@EnableConfigurationProperties(ReliableTaskProperties.class)
@ConditionalOnProperty(
        prefix = "xjtu.iron.reliable-task",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ReliableTaskAutoConfiguration {

    /** Starter 提供的默认任务 ID 生成器 Bean 名称。 */
    public static final String TASK_ID_GENERATOR_BEAN = "reliableTaskIdGenerator";

    @Bean
    @ConditionalOnMissingBean
    public Clock reliableTaskClock() {
        return Clock.systemUTC();
    }

    @Bean(name = TASK_ID_GENERATOR_BEAN)
    @ConditionalOnMissingBean(name = TASK_ID_GENERATOR_BEAN)
    public StringIdGenerator reliableTaskIdGenerator() {
        return IdGenerators.uuidV7();
    }

    @Bean
    @ConditionalOnMissingBean
    public ReliableTaskRuntimePolicy reliableTaskRuntimePolicy(ReliableTaskProperties properties) {
        return new ReliableTaskRuntimePolicy(
                properties.getDefaultMaxAttempts(),
                properties.getScanBucketCount(),
                properties.getLeaseDuration(),
                properties.getFailureRetryDelay()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public ReliableTaskHandlerRegistry reliableTaskHandlerRegistry(
            ObjectProvider<ReliableTaskHandler> handlers) {
        List<ReliableTaskHandler> values = handlers.orderedStream().toList();
        return new DefaultReliableTaskHandlerRegistry(values);
    }

    @Bean
    @ConditionalOnMissingBean
    public ReliableTaskEngine reliableTaskEngine(
            ReliableTaskRepository repository,
            ReliableTaskHandlerRegistry handlerRegistry,
            ReliableTaskRuntimePolicy policy,
            Clock clock,
            ReliableTaskProperties properties) {
        String configuredOwner = properties.getOwnerId();
        String ownerId = configuredOwner == null || configuredOwner.isBlank()
                ? "reliable-task-" + UUID.randomUUID()
                : configuredOwner.trim();
        return new ReliableTaskEngine(repository, handlerRegistry, policy, clock, ownerId);
    }

    @Bean
    @ConditionalOnBean(ReliableTaskEngine.class)
    @ConditionalOnMissingBean(ReliableTaskClient.class)
    public ReliableTaskClient reliableTaskClient(
            ReliableTaskRepository repository,
            ReliableTaskEngine engine,
            ReliableTaskRuntimePolicy policy,
            @Qualifier(TASK_ID_GENERATOR_BEAN) StringIdGenerator idGenerator,
            Clock clock) {
        return new DefaultReliableTaskClient(repository, engine, policy, idGenerator, clock);
    }

    @Bean
    @ConditionalOnBean(ReliableTaskEngine.class)
    @ConditionalOnMissingBean(ReliableTaskScanner.class)
    public ReliableTaskScanner reliableTaskScanner(
            ReliableTaskRepository repository,
            ReliableTaskEngine engine,
            Clock clock) {
        return new DefaultReliableTaskScanner(repository, engine, clock);
    }

    @Bean
    @ConditionalOnBean(ReliableTaskRepository.class)
    @ConditionalOnMissingBean(ReliableTaskAdminClient.class)
    public ReliableTaskAdminClient reliableTaskAdminClient(
            ReliableTaskRepository repository,
            Clock clock) {
        return new DefaultReliableTaskAdminClient(repository, clock);
    }
}
