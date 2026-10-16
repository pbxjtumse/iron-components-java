package com.xjtu.iron.concurrency.starter.configuration;

import com.xjtu.iron.concurrency.core.metrics.ConcurrencyMetricsRecorder;
import com.xjtu.iron.concurrency.core.metrics.NoopConcurrencyMetricsRecorder;
import com.xjtu.iron.concurrency.core.metrics.ThreadPoolMetricName;
import com.xjtu.iron.concurrency.core.spi.ThreadPoolRegistry;
import com.xjtu.iron.concurrency.starter.observability.MicrometerConcurrencyMetricsRecorder;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 并发组件可观测性自动装配。
 */
@AutoConfiguration(before = XjtuIronConcurrencyExecutionAutoConfiguration.class)
public class XjtuIronConcurrencyObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean
    public ConcurrencyMetricsRecorder concurrencyMetricsRecorder(MeterRegistry meterRegistry) {
        return new MicrometerConcurrencyMetricsRecorder(meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public ConcurrencyMetricsRecorder noopConcurrencyMetricsRecorder() {
        return new NoopConcurrencyMetricsRecorder();
    }

    @Bean(name = "ironConcurrencyThreadPoolMeterBinder")
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean(name = "ironConcurrencyThreadPoolMeterBinder")
    public MeterBinder threadPoolMeterBinder(ThreadPoolRegistry threadPoolRegistry) {
        return meterRegistry -> threadPoolRegistry.snapshot().forEach((poolName, executor) -> {
            Tags tags = Tags.of("component", "xjtu-iron-concurrency", "pool", poolName);
            for (ThreadPoolMetricName metricName : ThreadPoolMetricName.values()) {
                Gauge.builder(metricName.meterName(), executor, metricName::value)
                        .description(metricName.description())
                        .tags(tags)
                        .register(meterRegistry);
            }
        });
    }
}
