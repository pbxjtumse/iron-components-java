package com.xjtu.iron.retry.config.autoconfigure;

import com.xjtu.iron.foundation.time.ClockProvider;
import com.xjtu.iron.retry.api.execution.RetryExecutor;
import com.xjtu.iron.retry.api.policy.RetryPolicyRegistry;
import com.xjtu.iron.retry.core.time.RetryClock;
import com.xjtu.iron.retry.core.time.RetrySleeper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证核心 Spring Boot 自动配置和策略继承。
 */
class RetryAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RetryAutoConfiguration.class));

    @Test
    void shouldCreateExecutorAndResolveInheritedPolicy() {
        contextRunner
                .withPropertyValues(
                        "xjtu.iron.retry.publish-spring-events=false",
                        "xjtu.iron.retry.policies.base.max-attempts=4",
                        "xjtu.iron.retry.policies.base.max-duration=3s",
                        "xjtu.iron.retry.policies.base.retry-on[0]="
                                + IOException.class.getName(),
                        "xjtu.iron.retry.policies.base.retry-failure-category=TRANSIENT",
                        "xjtu.iron.retry.policies.base.max-cause-depth=8",
                        "xjtu.iron.retry.policies.base.backoff.type=FIXED",
                        "xjtu.iron.retry.policies.base.backoff.delay=10ms",
                        "xjtu.iron.retry.policies.remote.base-policy=base",
                        "xjtu.iron.retry.policies.remote.max-duration=2s"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(RetryExecutor.class);
                    assertThat(context).hasSingleBean(RetryClock.class);
                    assertThat(context).hasSingleBean(ClockProvider.class);
                    assertThat(context).hasSingleBean(RetrySleeper.class);
                    assertThat(context).hasBean(
                            RetryAutoConfiguration.RETRY_ID_GENERATOR_BEAN_NAME
                    );
                    RetryPolicyRegistry registry = context.getBean(
                            RetryPolicyRegistry.class
                    );
                    assertThat(registry.getRequired("remote").getMaxAttempts())
                            .isEqualTo(4);
                    assertThat(registry.getRequired("remote").getMaxDuration())
                            .hasSeconds(2);
                    assertThat(registry.getRequired("remote").getMaxCauseDepth())
                            .isEqualTo(8);
                });
    }


    @Test
    void shouldBackOffWhenDisabled() {
        contextRunner
                .withPropertyValues("xjtu.iron.retry.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(RetryExecutor.class));
    }
}
