package com.xjtu.iron.governance.starter.autoconfigure;

import com.xjtu.iron.governance.api.template.GovernanceTemplate;
import com.xjtu.iron.governance.starter.properties.GovernanceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class GovernanceAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GovernanceAutoConfiguration.class));

    @Test
    void shouldBindGovernancePropertiesWithUnifiedPrefix() {
        contextRunner
                .withPropertyValues(
                        "xjtu.iron.governance.enabled=true",
                        "xjtu.iron.governance.timeout-executor.core-pool-size=21",
                        "xjtu.iron.governance.timeout-executor.max-pool-size=101",
                        "xjtu.iron.governance.timeout-executor.queue-capacity=1001",
                        "xjtu.iron.governance.default-policy.timeout.enabled=true",
                        "xjtu.iron.governance.default-policy.timeout.timeout=2s")
                .run(context -> {
                    assertThat(context).hasSingleBean(GovernanceProperties.class);
                    GovernanceProperties properties = context.getBean(GovernanceProperties.class);
                    assertThat(properties.getTimeoutExecutor().getCorePoolSize()).isEqualTo(21);
                    assertThat(properties.getTimeoutExecutor().getMaxPoolSize()).isEqualTo(101);
                    assertThat(properties.getTimeoutExecutor().getQueueCapacity()).isEqualTo(1001);
                    assertThat(properties.getDefaultPolicy().getTimeout().isEnabled()).isTrue();
                    assertThat(properties.getDefaultPolicy().getTimeout().getTimeout()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(context).hasSingleBean(GovernanceTemplate.class);
                });
    }

    @Test
    void shouldDisableGovernanceAutoConfiguration() {
        contextRunner
                .withPropertyValues("xjtu.iron.governance.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(GovernanceTemplate.class));
    }
}
