package com.xjtu.iron.distributed.lock.starter.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证没有锁 Provider 时 Starter 不创建客户端。 */
class DistributedLockAutoConfigurationTest {

    /** 被测自动配置上下文。 */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RedisDistributedLockAutoConfiguration.class,
                    RedissonDistributedLockAutoConfiguration.class,
                    MyBatisFencingTokenAutoConfiguration.class,
                    DistributedLockAutoConfiguration.class,
                    DistributedLockActuatorAutoConfiguration.class
            ));

    @Test
    void shouldNotCreateClientWhenNoLockProviderExists() {
        contextRunner.run(context -> assertThat(context)
                .doesNotHaveBean(com.xjtu.iron.distributed.lock.api.client.DistributedLockClient.class));
    }
}
