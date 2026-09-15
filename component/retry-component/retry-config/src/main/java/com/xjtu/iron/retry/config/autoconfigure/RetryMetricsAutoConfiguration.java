package com.xjtu.iron.retry.config.autoconfigure;

import com.xjtu.iron.retry.config.observation.MicrometerRetryListener;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/** 在 Micrometer 存在且指标开关开启时装配指标监听器。 */
@AutoConfiguration(before = RetryAutoConfiguration.class)
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(
        prefix = "xjtu.iron.retry",
        name = {"enabled", "metrics-enabled"},
        havingValue = "true",
        matchIfMissing = true
)
public class RetryMetricsAutoConfiguration {

    /** 创建默认 Micrometer 重试监听器。 */
    @Bean
    @ConditionalOnMissingBean(MicrometerRetryListener.class)
    public MicrometerRetryListener micrometerRetryListener(MeterRegistry meterRegistry) {
        return new MicrometerRetryListener(meterRegistry);
    }
}
