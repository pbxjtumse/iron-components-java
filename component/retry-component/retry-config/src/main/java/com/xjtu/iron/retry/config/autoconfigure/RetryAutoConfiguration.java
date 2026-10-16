package com.xjtu.iron.retry.config.autoconfigure;

import com.xjtu.iron.foundation.id.api.StringIdGenerator;
import com.xjtu.iron.foundation.id.factory.IdGenerators;
import com.xjtu.iron.foundation.id.registry.StringIdGeneratorRegistry;
import com.xjtu.iron.retry.api.event.RetryListener;
import com.xjtu.iron.retry.api.execution.RetryExecutor;
import com.xjtu.iron.retry.api.policy.RetryPolicy;
import com.xjtu.iron.retry.api.policy.RetryPolicyRegistry;
import com.xjtu.iron.retry.config.observation.SpringApplicationRetryListener;
import com.xjtu.iron.retry.config.properties.RetryPolicyConfigurationLoader;
import com.xjtu.iron.retry.config.properties.RetryProperties;
import com.xjtu.iron.retry.core.executor.DefaultRetryExecutor;
import com.xjtu.iron.retry.core.policy.DefaultRetryPolicyRegistry;
import com.xjtu.iron.retry.core.time.RetryClock;
import com.xjtu.iron.retry.core.time.RetrySleeper;
import com.xjtu.iron.retry.core.time.SystemRetryClock;
import com.xjtu.iron.retry.core.time.ThreadSleepRetrySleeper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * 装配 retry-component 的核心 Spring Boot Bean。
 */
@AutoConfiguration
@EnableConfigurationProperties(RetryProperties.class)
@ConditionalOnProperty(
        prefix = "xjtu.iron.retry",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RetryAutoConfiguration {

    public static final String RETRY_ID_GENERATOR_BEAN_NAME = "retryIdGenerator";

    /** Foundation 字符串生成器注册表中的重试用途名称。 */
    public static final String RETRY_ID_GENERATOR_REGISTRY_NAME = "retry";

    /**
     * 为重试逻辑执行选择专用字符串 ID 生成器。
     *
     * <p>存在 Foundation Registry 时必须包含 retry 名称；没有 Registry 时使用 UUID v7。业务系统
     * 仍可通过固定 Bean 名称覆盖选择结果，而不需要重新定义整个 RetryExecutor。</p>
     */
    @Bean(name = RETRY_ID_GENERATOR_BEAN_NAME)
    @ConditionalOnMissingBean(name = RETRY_ID_GENERATOR_BEAN_NAME)
    public StringIdGenerator retryIdGenerator(
            ObjectProvider<StringIdGeneratorRegistry> registryProvider) {
        StringIdGeneratorRegistry registry = registryProvider.getIfAvailable();
        if (registry == null) {
            return IdGenerators.uuidV7();
        }
        return registry.require(RETRY_ID_GENERATOR_REGISTRY_NAME);
    }

    /** 在启用 Spring 事件桥接时注册默认监听器。 */
    @Bean
    @ConditionalOnProperty(
            prefix = "xjtu.iron.retry",
            name = "publish-spring-events",
            havingValue = "true",
            matchIfMissing = true
    )
    @ConditionalOnMissingBean(SpringApplicationRetryListener.class)
    public SpringApplicationRetryListener springApplicationRetryListener(
            ApplicationEventPublisher publisher) {
        return new SpringApplicationRetryListener(publisher);
    }

    /** 注册可被业务覆盖的系统时钟实现。 */
    @Bean
    @ConditionalOnMissingBean
    public RetryClock retryClock() {
        return new SystemRetryClock();
    }

    /** 注册可被业务覆盖的同步等待实现。 */
    @Bean
    @ConditionalOnMissingBean
    public RetrySleeper retrySleeper() {
        return new ThreadSleepRetrySleeper();
    }

    /** 解析全部命名策略并注册到默认策略注册表。 */
    @Bean
    @ConditionalOnMissingBean
    public RetryPolicyRegistry retryPolicyRegistry(RetryProperties properties) {
        DefaultRetryPolicyRegistry registry = new DefaultRetryPolicyRegistry();
        RetryPolicyConfigurationLoader loader = new RetryPolicyConfigurationLoader();
        for (RetryPolicy retryPolicy : loader.load(properties).values()) {
            registry.register(retryPolicy);
        }
        return registry;
    }

    /** 汇总有序监听器和可替换基础设施后创建默认执行器。 */
    @Bean
    @ConditionalOnMissingBean
    public RetryExecutor retryExecutor(
            RetryPolicyRegistry retryPolicyRegistry,
            ObjectProvider<RetryListener> retryListeners,
            RetrySleeper retrySleeper,
            RetryClock retryClock,
            @Qualifier(RETRY_ID_GENERATOR_BEAN_NAME)
            StringIdGenerator retryIdGenerator) {
        List<RetryListener> listeners = retryListeners.orderedStream().toList();
        return new DefaultRetryExecutor(
                retryPolicyRegistry,
                listeners,
                retrySleeper,
                retryClock,
                retryIdGenerator
        );
    }
}
